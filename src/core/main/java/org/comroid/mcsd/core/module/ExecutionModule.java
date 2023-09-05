package org.comroid.mcsd.core.module;

import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.extern.java.Log;
import org.comroid.api.Component;
import org.comroid.api.DelegateStream;
import org.comroid.api.Event;
import org.comroid.api.Polyfill;
import org.comroid.api.io.FileHandle;
import org.comroid.api.os.OS;
import org.comroid.mcsd.api.model.Status;
import org.comroid.mcsd.core.entity.Server;
import org.comroid.mcsd.util.Utils;
import org.comroid.util.Debug;
import org.comroid.util.PathUtil;
import org.comroid.util.Stopwatch;
import org.jetbrains.annotations.Nullable;

import java.io.PrintStream;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntFunction;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Log
@Getter
@ToString
@Component.Requires(FileModule.class)
@FieldDefaults(level = AccessLevel.PRIVATE)
public final class ExecutionModule extends ConsoleModule {
    public static final Pattern DonePattern = pattern("Done \\((?<time>[\\d.]+)s\\).*\\r?\\n?");
    public static final Pattern StopPattern = pattern("Closing server.*\\r?\\n?");
    public static final Pattern CrashPattern = Pattern.compile(".*(crash-\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2}-server.txt).*");

    public static final Factory<ExecutionModule> Factory = new Factory<>(ExecutionModule.class) {
        @Override
        public ExecutionModule create(Server server) {
            return new ExecutionModule(server);
        }
    };

    final AtomicBoolean manualShutdown = new AtomicBoolean(false);
    Process process;
    PrintStream in;
    DelegateStream.IO oe;
    CompletableFuture<Duration> done;
    CompletableFuture<Void> stop;

    private ExecutionModule(Server server) {
        super(server);
    }

    @Override
    public CompletableFuture<String> execute(String input, @Nullable Pattern terminator) {
        if (input == null || input.isBlank())
            return CompletableFuture.failedFuture(new RuntimeException("Command is empty"));
        in.println(input);
        in.flush();
        return Utils.listenForPattern(bus, terminator != null ? terminator
                        : ConsoleModule.commandPattern(input.split(" ")[0]))
                .listen().once()
                .thenApply(Event::getData)
                .thenApply(Matcher::group);
    }

    @Override
    @SneakyThrows
    protected void $tick() {
        super.$tick();
        if (manualShutdown.get() || server.isEnabled() && process.isAlive())
            return;
        log.info("Starting " + server);
        final var stopwatch = Stopwatch.start("startup-" + server.getId());
        var exec = PathUtil.findExec("java").orElseThrow();
        process = Runtime.getRuntime().exec(server.getCustomCommand() == null ? new String[]{
                        exec.getAbsolutePath(),
                        "-Xmx%dG".formatted(server.getRamGB()),
                        "-jar", "server.jar", Debug.isDebug() && OS.isWindows ? "" : "nogui"} : server.getCustomCommand().split(" "),
                new String[0],
                new FileHandle(server.getDirectory(), true));

        in = new PrintStream(process.getOutputStream(), true);
        oe = DelegateStream.IO.process(process).redirectToEventBus(bus);
        if (Debug.isDebug()) oe.redirectToSystem();

        this.done = Utils.listenForPattern(bus, DonePattern)
                .mapData(m -> m.group("time"))
                .mapData(Double::parseDouble)
                .mapData(x -> Duration.ofMillis((long) (x * 1000)))
                .listen().once()
                .thenApply($ -> stopwatch.stop());
        done.thenCompose(d -> {
            var t = Polyfill.durationString(d);
            var msg = "Took " + t + " to start";
            return server.component(StatusModule.class).assertion()
                    .pushStatus((server.isMaintenance() ? Status.in_maintenance_mode : Status.online).new Message(msg));
        }).thenAccept(msg -> log.info(server + " " + msg.getMessage())).join();

        this.stop = CompletableFuture.anyOf(process.onExit(),
                        Utils.listenForPattern(bus, StopPattern).listen().once())
                .thenRun(server::terminate);
    }

    public CompletableFuture<?> shutdown(final String reason, final int warnSeconds) {
        return CompletableFuture.supplyAsync(() -> {
            server.component(StatusModule.class).assertion()
                    .pushStatus(Status.shutting_down.new Message(reason));
            final var msg = (IntFunction<String>) t -> "say Server will shut down in %d seconds (%s)".formatted(t, reason);
            int time = warnSeconds;

            try {
                while (time > 0) {
                    in.println(msg.apply(time));
                    if (time >= 10) {
                        time /= 2;
                        Thread.sleep(TimeUnit.SECONDS.toMillis(time));
                    } else {
                        time -= 1;
                        Thread.sleep(1000);
                    }
                }
            } catch (InterruptedException e) {
                log.log(Level.SEVERE, "Could not wait for shutdown timeout", e);
            }

            in.println("stop");
            return null;
        }).thenCompose($->stop);
    }

    @Override
    protected void $terminate() {
        if (!stop.isDone())
            shutdown("Forced Shutdown", 5);
        stop.join();
        super.$terminate();
    }

    @Override
    @SneakyThrows
    public void closeSelf() {
        if (process == null || !process.isAlive())
            return;

        server.component(StatusModule.class).assertion()
                .pushStatus(Status.offline);
        if (process.isAlive())
            shutdown("Host shutdown", 2).join();
        if (process.isAlive())
            process.destroy();
    }
}
