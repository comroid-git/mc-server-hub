package org.comroid.mcsd.core.module.local;

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
import org.comroid.mcsd.core.module.console.ConsoleModule;
import org.comroid.mcsd.core.module.status.StatusModule;
import org.comroid.mcsd.core.module.status.UpdateModule;
import org.comroid.mcsd.util.Utils;
import org.comroid.util.Debug;
import org.comroid.util.MultithreadUtil;
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
@Component.Requires(UpdateModule.class)
@FieldDefaults(level = AccessLevel.PRIVATE)
public final class LocalExecutionModule extends ConsoleModule {
    public static final Pattern DonePattern = pattern("Done \\((?<time>[\\d.]+)s\\).*\\r?\\n?.*?");
    public static final Pattern StopPattern = pattern("Closing [sS]erver.*\\r?\\n?.*?");
    public static final Pattern CrashPattern = Pattern.compile(".*(crash-\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2}-parent.txt).*.*?");

    public static final Factory<LocalExecutionModule> Factory = new Factory<>(LocalExecutionModule.class) {
        @Override
        public LocalExecutionModule create(Server parent) {
            return new LocalExecutionModule(parent);
        }
    };

    final AtomicBoolean manualShutdown = new AtomicBoolean(false);
    Process process;
    PrintStream in;
    DelegateStream.IO oe;
    CompletableFuture<Duration> done;
    CompletableFuture<Void> stop;

    private LocalExecutionModule(Server parent) {
        super(parent);
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
    protected synchronized void $tick() {
        if (parent.isEnabled() && (manualShutdown.get() || (process != null && process.isAlive())))
            return;
        log.info("Starting " + parent);
        parent.component(UpdateModule.class).ifPresent(mod->mod.runUpdate(false).join());
        parent.component(StatusModule.class).assertion().pushStatus(Status.starting);
        final var stopwatch = Stopwatch.start("startup-" + parent.getId());
        var exec = PathUtil.findExec("java").orElseThrow();
        process = Runtime.getRuntime().exec(parent.getCustomCommand() == null ? new String[]{
                        exec.getAbsolutePath(),
                        "-Xmx%dG".formatted(parent.getRamGB()),
                        "-jar", "parent.jar", Debug.isDebug() && OS.isWindows ? "" : "nogui"} : parent.getCustomCommand().split(" "),
                new String[0],
                new FileHandle(parent.getDirectory(), true));

        in = new PrintStream(process.getOutputStream(), true);
        oe = DelegateStream.IO.process(process);
        if (Debug.isDebug())
            oe.redirectToSystem();
        oe.redirectToEventBus(bus);

        this.done = Utils.listenForPattern(bus, DonePattern)
                .mapData(m -> m.group("time"))
                .mapData(Double::parseDouble)
                .mapData(x -> Duration.ofMillis((long) (x * 1000)))
                .listen().once()
                .thenApply($ -> stopwatch.stop());
        done.thenCompose(d -> {
            var t = Polyfill.durationString(d);
            var msg = "Took " + t + " to start";
            return parent.component(StatusModule.class).assertion()
                    .pushStatus((parent.isMaintenance() ? Status.in_maintenance_mode : Status.online).new Message(msg));
        }).thenAccept(msg -> log.info(parent + " " + msg.getMessage()))
                .exceptionally(Polyfill.exceptionLogger());

        this.stop = MultithreadUtil.firstOf(process.onExit(),
                        Utils.listenForPattern(bus, StopPattern).listen().once())
                .thenRun(parent::terminate)
                .exceptionally(Polyfill.exceptionLogger());
    }

    public CompletableFuture<?> shutdown(final String reason, final int warnSeconds) {
        return CompletableFuture.supplyAsync(() -> {
            parent.component(StatusModule.class).assertion()
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
            shutdown("Forced Shutdown", 5)
                    .completeOnTimeout(null, 20, TimeUnit.SECONDS)
                    .join();
        super.$terminate();
    }

    @Override
    @SneakyThrows
    public void closeSelf() {
        if (process == null || !process.isAlive())
            return;

        parent.component(StatusModule.class).assertion()
                .pushStatus(Status.offline);
        if (process.isAlive())
            terminate();
        if (process.isAlive())
            process.destroy();
    }
}