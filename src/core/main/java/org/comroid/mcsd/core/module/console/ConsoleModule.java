package org.comroid.mcsd.core.module.console;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import lombok.extern.java.Log;
import org.comroid.api.Event;
import org.comroid.mcsd.core.entity.Server;
import org.comroid.mcsd.core.module.ServerModule;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

@Log
@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
public abstract class ConsoleModule extends ServerModule {
    public static final Pattern McsdPattern = commandPattern("mcsd");
    public static Pattern commandPattern(String command) {return pattern("(?<username>[\\S\\w_-]+) issued parent command: /"+command+" (?<command>[\\w\\s_-]+)\\r?\\n?.*");}

    protected Event.Bus<String> bus;

    public ConsoleModule(Server parent) {
        super(parent);
    }

    @Override
    protected void $initialize() {
        bus = new Event.Bus<>();
    }

    @Override
    protected void $terminate() {
        bus.close();
        super.$terminate();
    }

    public static Pattern pattern(@NotNull @Language("RegExp") String pattern) {
        return Pattern.compile(".*INFO]( \\[\\w*/\\w*])?: "+pattern);
    }

    public CompletableFuture<String> execute(String input) {
        return execute(input, null);
    }

    public abstract CompletableFuture<String> execute(String input, @Nullable Pattern terminator);
}
