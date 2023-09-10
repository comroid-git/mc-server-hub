package org.comroid.mcsd.core.module.console;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.FieldDefaults;
import lombok.extern.java.Log;
import org.comroid.api.Command;
import org.comroid.api.Component;
import org.comroid.mcsd.core.entity.MinecraftProfile;
import org.comroid.mcsd.core.entity.Server;
import org.comroid.mcsd.core.module.ServerModule;
import org.comroid.mcsd.core.repo.MinecraftProfileRepo;
import org.comroid.mcsd.util.McFormatCode;
import org.comroid.mcsd.util.Tellraw;
import org.comroid.mcsd.util.Utils;
import org.comroid.util.Streams;
import org.comroid.util.Token;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.regex.Pattern;

import static org.comroid.mcsd.core.util.ApplicationContextProvider.bean;

@Log
@Getter
@ToString
@FieldDefaults(level = AccessLevel.PRIVATE)
@Component.Requires(ConsoleModule.class)
public class McsdCommandModule extends ServerModule implements Command.Handler {
    public static final Pattern McsdPattern = ConsoleModule.commandPattern("mcsd");
    public static final Factory<McsdCommandModule> Factory = new Factory<>(McsdCommandModule.class) {
        @Override
        public McsdCommandModule create(Server server) {
            return new McsdCommandModule(server);
        }
    };

    final Command.Manager cmdr = new Command.Manager(this);
    ConsoleModule console;

    protected McsdCommandModule(Server server) {
        super(server);
    }

    @Override
    protected void $initialize() {
        ;
        console = server.component(ConsoleModule.class).assertion();

        addChildren(Utils.listenForPattern(console.bus, McsdPattern).subscribeData(matcher -> {
            var username = matcher.group("username");
            var profile = bean(MinecraftProfileRepo.class).get(username);
            var command = matcher.group("command");
            cmdr.execute(command.replaceAll("\r?\n", ""), profile);
        }));
    }

    @Override
    public void handleResponse(Command.Delegate cmd, @NotNull Object response, Object... args) {
        var profile = Arrays.stream(args)
                .flatMap(Streams.cast(MinecraftProfile.class))
                .findAny()
                .orElseThrow();
        var tellraw = Tellraw.Command.builder()
                .selector(profile.getName())
                .component(McFormatCode.Gray.text("<").build())
                .component(McFormatCode.Light_Purple.text("mcsd").build())
                .component(McFormatCode.Gray.text("> ").build())
                .component(McFormatCode.Reset.text(response.toString()).build())
                .build()
                .toString();
        console.execute(tellraw); // todo: tellraw data often too long
        log.finer(tellraw);
    }

    @Command
    public String link(MinecraftProfile profile) {
        final var profiles = bean(MinecraftProfileRepo.class);
        String code;
        do {
            code = Token.random(6, false);
        } while (profiles.findByVerification(code).isPresent());
        profile.setVerification(code);
        profiles.save(profile);
        return "Please run this command on discord: /verify " + code;
    }
}
