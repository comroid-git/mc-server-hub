package org.comroid.mcsd.core.module.player;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.FieldDefaults;
import lombok.extern.java.Log;
import org.comroid.api.Component;
import org.comroid.api.Event;
import org.comroid.mcsd.api.dto.ChatMessage;
import org.comroid.mcsd.core.entity.Server;
import org.comroid.mcsd.core.module.console.ConsoleModule;
import org.comroid.mcsd.core.module.ServerModule;
import org.comroid.mcsd.util.McFormatCode;
import org.comroid.mcsd.util.Tellraw;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.time.Instant.now;
import static org.comroid.mcsd.core.util.ApplicationContextProvider.bean;

@Log
@Getter
@ToString
@FieldDefaults(level = AccessLevel.PRIVATE)
@Component.Requires(ConsoleModule.class)
public class ChatModule extends ServerModule {
    public static final Duration TickerTimeout = Duration.ofMinutes(15);
    public static final Pattern ChatPattern = ConsoleModule.pattern(
            "([(\\[{<](?<prefix>[\\w\\s_-]+)[>}\\])]\\s?)*" +
            //"([(\\[{<]" +
            "<" +
            "(?<username>[\\w\\S_-]+)" +
            ">\\s?" +
            //"[>}\\])]\\s?)\\s?" +
            "([(\\[{<](?<suffix>[\\w\\s_-]+)[>}\\])]\\s?)*" +
            "(?<message>.+)\\r?\\n?.*");
    public static final Pattern BroadcastPattern = ConsoleModule.pattern(
            "(?<username>[\\S\\w_-]+) issued server command: " +
            "/(?<command>(me)|(say)|(broadcast)) " +
            "(?<message>.+)\\r?\\n?.*");
    public static final Pattern PlayerEventPattern = ConsoleModule.pattern(
            "(?<username>[\\S\\w_-]+) " +
            "(?<message>((joined|left) the game|has (made the advancement|completed the challenge) " +
            "(\\[(?<advancement>[\\w\\s]+)])))\\r?\\n?");
    public static final Factory<ChatModule> Factory = new Factory<>(ChatModule.class) {
        @Override
        public ChatModule create(Server server) {
            return new ChatModule(server);
        }
    };

    final AtomicReference<TickerMessage> lastTickerMessage = new AtomicReference<>(new TickerMessage(now(),-1));
    protected Event.Bus<ChatMessage> bus;

    private ChatModule(Server server) {
        super(server);
    }

    @Override
    @SuppressWarnings({"RedundantCast", "RedundantTypeArguments"})
    protected void $initialize() {
        var console = server.component(ConsoleModule.class)
                .orElseThrow(()->new InitFailed("No Console module is loaded"));
        addChildren(bus = console.getBus().<Matcher>mapData(str -> Stream.of(ChatPattern, BroadcastPattern, PlayerEventPattern)
                        .<Matcher>flatMap(pattern -> {
                            var matcher = pattern.matcher(str);
                            if (matcher.matches())
                                return Stream.of(matcher);
                            return Stream.<Matcher>empty();
                        })
                        .findAny()
                        .<Matcher>orElse((Matcher)null))
                .mapData(matcher -> {
                    var pattern = matcher.pattern().toString();
                    var event = !pattern.contains("prefix");
                    var broadcast = pattern.contains("command");
                    var username = matcher.group("username");
                    var message = matcher.group("message");
                    if (event) message = StringUtils.capitalize(message);
                    return new ChatMessage(username, message, event || broadcast);
                })
                .peekData(msg -> log.log(Level.FINE, "[CHAT @ %s] <%s> %s".formatted(server, msg.getUsername(), msg))));
    }

    @Override
    protected void $tick() {
        var console = server.component(ConsoleModule.class);
        var msgs = server.getTickerMessages();
        var last = lastTickerMessage.get();
        if (console.isNull() || msgs.isEmpty() || last.time.plus(TickerTimeout).isAfter(now()))
            return;
        var i = last.index + 1;
        if (i >= msgs.size())
            i = 0;
        var msg = msgs.get(i);
        var cmd = Tellraw.Command.builder()
                .selector(Tellraw.Selector.Base.ALL_PLAYERS)
                .component(McFormatCode.Gray.text("<").build())
                .component(McFormatCode.Light_Purple.text(msg).build())
                .component(McFormatCode.Gray.text("> ").build())
                .build().toString();
        console.assertion().execute(cmd);
    }

    private record TickerMessage(Instant time, int index) {}
}
