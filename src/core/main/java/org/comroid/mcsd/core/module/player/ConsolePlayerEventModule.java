package org.comroid.mcsd.core.module.player;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.FieldDefaults;
import lombok.extern.java.Log;
import org.comroid.api.Component;
import org.comroid.api.Event;
import org.comroid.mcsd.api.dto.PlayerEvent;
import org.comroid.mcsd.core.entity.Server;
import org.comroid.mcsd.core.module.console.ConsoleModule;
import org.comroid.mcsd.core.module.ServerModule;
import org.comroid.mcsd.util.McFormatCode;
import org.comroid.mcsd.util.Tellraw;
import org.comroid.util.Streams;
import org.comroid.util.Switch;
import org.intellij.lang.annotations.Language;
import org.springframework.util.StringUtils;

import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
public class ConsolePlayerEventModule extends ServerModule {
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
            "(?<username>[\\S\\w_-]+) issued parent command: " +
                    "/(?<command>(me)|(say)|(broadcast)) " +
                    "(?<message>.+)\\r?\\n?.*");
    public static final Pattern JoinLeavePattern = ConsoleModule.pattern(
            "(?<username>[\\S\\w_-]+) " +
                    "(?<message>(joined|left) the game)\\r?\\n?");
    public static final Pattern AchievementPattern = ConsoleModule.pattern(
            "(?<username>[\\S\\w_-]+) " +
                    "(?<message>has (made the advancement|completed the challenge) " +
                    "(\\[(?<advancement>[\\w\\s]+)]))\\r?\\n?");
    public static final Factory<ConsolePlayerEventModule> Factory = new Factory<>(ConsolePlayerEventModule.class) {
        @Override
        public ConsolePlayerEventModule create(Server parent) {
            return new ConsolePlayerEventModule(parent);
        }
    };
    public static final Pattern DeathPatternBase = Pattern.compile("^%1\\$s (?<message>[\\w\\s%$]+)$");
    public static final @Language("RegExp") String DeathPatternScheme = ".*INFO]: (?<username>[\\S\\w-_]+) (?<message>%s)\\r?\\n?.*";
    @SuppressWarnings("RegExpDuplicateCharacterInClass")
    public static final @Language("RegExp") String CleanWord_Spaced = "\\\\[?([\\\\s\\\\w\\\\-_]+)]?";
    public static final List<Pattern> DeathMessagePatterns = new ArrayList<>();

    final AtomicReference<TickerMessage> lastTickerMessage = new AtomicReference<>(new TickerMessage(now(), -1));
    protected Event.Bus<PlayerEvent> bus;

    private ConsolePlayerEventModule(Server parent) {
        super(parent);
    }

    @Override
    @SuppressWarnings({"RedundantCast", "RedundantTypeArguments"})
    protected void $initialize() {
        try (var url = new URL("https://raw.githubusercontent.com/misode/mcmeta/assets-json/assets/minecraft/lang/en_us.json").openStream()) {
            var obj = bean(ObjectMapper.class).readTree(url);
            Streams.of(obj.fields(),obj.size())
                    .filter(e->e.getKey().startsWith("death."))
                    .map(Map.Entry::getValue)
                    .map(JsonNode::asText)
                    .filter(Objects::nonNull)
                    .flatMap(str -> {
                        var matcher = DeathPatternBase.matcher(str);
                        if (!matcher.matches())
                            return Stream.empty();
                        var msg = matcher.group("message");
                        //noinspection RedundantEscapeInRegexReplacement
                        var out = DeathPatternScheme.formatted(msg).replaceAll("%\\d\\$s", CleanWord_Spaced);
                        return Stream.of(out);
                    })
                    .map(Pattern::compile)
                    .forEach(DeathMessagePatterns::add);
            log.info(DeathMessagePatterns.size()+" death messages registered");
        } catch (Throwable t) {
            log.log(Level.INFO, "Unable to fetch death messages; disabling support for it", t);
        }

        var console = parent.component(ConsoleModule.class)
                .orElseThrow(() -> new InitFailed("No Console module is loaded"));
        addChildren(bus = console.getBus().<Matcher>mapData(str -> Stream.of(ChatPattern, BroadcastPattern, JoinLeavePattern, AchievementPattern)
                        .collect(Streams.append(DeathMessagePatterns))
                        .<Matcher>flatMap(pattern -> {
                            var matcher = pattern.matcher(str);
                            if (matcher.matches())
                                return Stream.of(matcher);
                            return Stream.<Matcher>empty();
                        })
                        .findAny()
                        .<Matcher>orElse((Matcher) null))
                .mapData(matcher -> {
                    var username = matcher.group("username");
                    var message = matcher.group("message");
                    //noinspection SuspiciousMethodCalls
                    var type = new Switch<>(() -> PlayerEvent.Type.Other)
                            .option(ChatPattern, PlayerEvent.Type.Chat)
                            .option(JoinLeavePattern, PlayerEvent.Type.JoinLeave)
                            .option(AchievementPattern, PlayerEvent.Type.Achievement)
                            .option(DeathMessagePatterns::contains, PlayerEvent.Type.Death)
                            .apply(matcher.pattern());
                    if (type != PlayerEvent.Type.Chat)
                        message = StringUtils.capitalize(message);
                    return new PlayerEvent(username, message, type);
                })
                .peekData(msg -> log.log(Level.FINE, "[CHAT @ %s] <%s> %s".formatted(parent, msg.getUsername(), msg))));
    }

    @Override
    protected void $tick() {
        var console = parent.component(ConsoleModule.class);
        var msgs = parent.getTickerMessages();
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

    private record TickerMessage(Instant time, int index) {
    }
}
