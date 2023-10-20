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
public class ConsolePlayerEventModule extends PlayerEventModule {
    public static final Factory<ConsolePlayerEventModule> Factory = new Factory<>(ConsolePlayerEventModule.class) {
        @Override
        public ConsolePlayerEventModule create(Server parent) {
            return new ConsolePlayerEventModule(parent);
        }
    };

    private ConsolePlayerEventModule(Server parent) {
        super(parent);
    }

    @Override
    @SuppressWarnings({"RedundantSuppression", "RedundantTypeArguments", "RedundantCast"}) // intellij is being weird
    protected Event.Bus<PlayerEvent> initEventBus() {
        var console = server.component(ConsoleModule.class).orElseThrow(() -> new InitFailed("No Console module is loaded"));
        return console.getBus()
                .<Matcher>mapData(str -> Stream.of(ChatPattern, BroadcastPattern, JoinLeavePattern, AchievementPattern)
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
                .peekData(msg -> log.log(Level.FINE, "[CHAT @ %s] <%s> %s".formatted(server, msg.getUsername(), msg)));
    }
}
