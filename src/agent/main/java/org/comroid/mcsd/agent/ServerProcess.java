package org.comroid.mcsd.agent;

import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.comroid.api.*;
import org.comroid.api.io.FileHandle;
import org.comroid.api.os.OS;
import org.comroid.mcsd.api.model.IStatusMessage;
import org.comroid.mcsd.api.model.Status;
import org.comroid.mcsd.core.entity.Backup;
import org.comroid.mcsd.core.entity.MinecraftProfile;
import org.comroid.mcsd.core.entity.Server;
import org.comroid.mcsd.core.entity.ServerUptimeEntry;
import org.comroid.mcsd.core.module.ConsoleModule;
import org.comroid.mcsd.core.module.ExecutionModule;
import org.comroid.mcsd.core.module.discord.DiscordConnection;
import org.comroid.mcsd.core.repo.BackupRepo;
import org.comroid.mcsd.core.repo.MinecraftProfileRepo;
import org.comroid.mcsd.core.repo.ServerRepo;
import org.comroid.mcsd.core.repo.ServerUptimeRepo;
import org.comroid.mcsd.util.McFormatCode;
import org.comroid.mcsd.util.Tellraw;
import org.comroid.util.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.net.URL;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.comroid.mcsd.core.util.ApplicationContextProvider.bean;

@Slf4j
@Getter
@Deprecated
public class ServerProcess extends ExecutionModule implements Startable, Command.Handler {
    // todo: improve these
    private final AtomicReference<CompletableFuture<@Nullable File>> currentBackup = new AtomicReference<>(CompletableFuture.completedFuture(null));
    private final AtomicBoolean updateRunning = new AtomicBoolean(false);
    private final AtomicInteger lastTicker = new AtomicInteger(0);
    private final @Deprecated @lombok.experimental.Delegate Event.Bus<String> bus = new Event.Bus<>();
    private final AgentRunner runner;
    private Command.Manager cmdr;
    private IStatusMessage previousStatus = Status.unknown_status;
    private IStatusMessage currentStatus = Status.unknown_status;

    public ServerProcess(AgentRunner runner, Server server) {
        super(server);
        this.runner = runner;
    }

    public State getState() {
        return process == null
                ? State.NotStarted
                : process.isAlive()
                ? State.Running
                : State.Exited;
    }

    public void pushStatus(IStatusMessage message) {
        if (currentStatus != null && currentStatus.getStatus() == message.getStatus()
                && Objects.equals(currentStatus.getMessage(), message.getMessage()))
            return; // do not push same status twice
        previousStatus = currentStatus;
        currentStatus = message;
        bean(ServerRepo.class).setStatus(server.getId(), message.getStatus());
        bean(Event.Bus.class, "eventBus").publish(server.getId().toString(), message);
        pushUptime();
    }

    public void pushUptime() {
        server.status()
                .thenCombine(getState() == State.Running
                                ? OS.current.getRamUsage(process.pid())
                                : CompletableFuture.completedFuture(0L),
                        (stat, ram) -> new ServerUptimeEntry(server,
                                currentStatus.getStatus(),
                                stat.getPlayers() != null ? stat.getPlayers().size() : stat.getPlayerCount(),
                                ram))
                .thenAccept(bean(ServerUptimeRepo.class)::save)
                .join();
    }

    public boolean pushMaintenance(boolean val) { //todo
        var is = server.isMaintenance();
        runner.getServers().setMaintenance(server.getId(), val);
        if (is && !val) {
            // disable maintenance
            in.println("whitelist off");
            pushStatus(Status.in_maintenance_mode.new Message("Maintenance has been turned off"));
        } else if (!is && val) {
            // enable maintenance
            in.println("whitelist on");
            pushStatus(Status.in_maintenance_mode);
        }
        return is != val;
    }

    @Override
    @SneakyThrows
    public void start() {
        if (!currentBackup.get().isDone() || updateRunning.get())
            return;
        if (getState() == State.Running)
            return;



        var botConId = server.getDiscordBot();
        if (botConId != null)
            addChildren(discord = new DiscordConnection(this));
        pushStatus(Status.starting);


        this.cmdr = new Command.Manager(this);
        addChildren(cmdr);
        listenForPattern(McsdPattern).subscribeData(this::runCommand);

        if (Debug.isDebug())
            //oe.redirectToLogger(log);
            oe.redirectToSystem();
    }

    //region Commands
    private void runCommand(Matcher matcher) {
        var username = matcher.group("username");
        var profile = bean(MinecraftProfileRepo.class).get(username);
        var command = matcher.group("command");
        cmdr.execute(command.replaceAll("\r?\n", ""), profile);
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
        in.println(tellraw); // todo: tellraw data often too long
        log.trace(tellraw);
        in.flush();
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
    //endregion

    public synchronized void runTicker() {
        var messages = server.getTickerMessages();
        if (messages == null || messages.isEmpty())
            return;
        if (lastTicker.get()>=messages.size())
            lastTicker.set(0);
        var msg = messages.get(lastTicker.getAndIncrement());
        var cmd = Tellraw.Command.builder()
                .selector(Tellraw.Selector.Base.ALL_PLAYERS)
                .component(McFormatCode.Gray.text("<").build())
                .component(McFormatCode.Light_Purple.text(msg).build())
                .component(McFormatCode.Gray.text("> ").build())
                .build().toString();
        in.println(cmd);
    }

    @SneakyThrows
    public CompletableFuture<File> runBackup(final boolean important) {
        if (!currentBackup.get().isDone()) {
            log.warn("A backup on server %s is already running".formatted(server));
            return currentBackup.get();
        }

        pushStatus(Status.running_backup);
        final var stopwatch = Stopwatch.start("backup-" + server.getId());

        var backupDir = new FileHandle(server.shCon().orElseThrow().getBackupsDir()).createSubDir(server.getName());
        if (!backupDir.exists() && !backupDir.mkdirs())
            return CompletableFuture.failedFuture(new RuntimeException("Could not create backup directory"));

        // todo: fix bugs from this
        var saveComplete = waitForOutput("INFO]: Saved the game");
        in.println("save-off");
        in.println("save-all");

        final var time = Instant.now();
        return saveComplete
                // wait for save to finish
                .thenCompose($ -> Archiver.find(Archiver.ReadOnly).zip()
                        // do run backup
                        .inputDirectory(server.path().toAbsolutePath())
                        .excludePattern("**cache/**")
                        .excludePattern("**libraries/**")
                        .excludePattern("**versions/**")
                        .excludePattern("**dynmap/web/**") // do not backup dynmap
                        .excludePattern("**.lock")
                        .outputPath(Paths.get(backupDir.getAbsolutePath(), "backup-" + PathUtil.sanitize(time)).toAbsolutePath())
                        .execute()
                        //.orTimeout(30, TimeUnit.SECONDS) // dev variant
                        .orTimeout(1, TimeUnit.HOURS)
                        .whenComplete((r, t) -> {
                            var stat = Status.online;
                            var duration = stopwatch.stop();
                            var sizeKb = r != null ? (r.length() / (1024)) : 0;
                            var msg = "Backup finished; took %s; size: %1.2fGB".formatted(
                                    Polyfill.durationString(duration),
                                    (double) sizeKb / (1024 * 1024));
                            if (r != null)
                                bean(BackupRepo.class)
                                        .save(new Backup(time, server, sizeKb, duration, r.getAbsolutePath(), important));
                            if (t != null) {
                                stat = Status.in_Trouble;
                                msg = "Unable to complete Backup";
                                log.error(msg + " for " + server, t);
                            }
                            in.println("save-on");
                            pushStatus(stat.new Message(msg));
                        }));
    }

    @SneakyThrows
    public boolean isJarUpToDate() {
        if (server.isForceCustomJar())
            return true;
        var serverJar = new FileHandle(server.path("server.jar").toFile());
        if (!serverJar.exists())
            return false;
        try (var source = new JSON.Deserializer(new URL(server.getJarInfoUrl()).openStream());
             var local = new FileInputStream(serverJar)) {
            var sourceMd5 = source.readObject().get("response").get("md5").asString("");
            var localMd5 = MD5.calculate(local);
            return sourceMd5.equals(localMd5);
        }
    }

    public boolean startUpdate() {
        return !isJarUpToDate() && updateRunning.compareAndSet(false, true);
    }

    public CompletableFuture<Boolean> runUpdate(String... args) {
        var flags = String.join("", args);
        pushStatus(Status.updating);

        return CompletableFuture.supplyAsync(() -> {
            try {
                // modify server.properties
                Properties prop;
                var serverProperties = new FileHandle(server.path("server.properties").toFile());
                if (!serverProperties.exists()) {
                    serverProperties.mkdirs();
                    serverProperties.createNewFile();
                }
                try (var in = new FileInputStream(serverProperties)) {
                    prop = server.updateProperties(in);
                }
                try (var out = new FileOutputStream(serverProperties, false)) {
                    prop.store(out, "Managed Server Properties by MCSD");
                }

                // download server.jar
                var serverJar = new FileHandle(server.path("server.jar").toFile());
                if (!serverJar.exists()) {
                    serverJar.mkdirs();
                    serverJar.createNewFile();
                } else if (!flags.contains("r") && isJarUpToDate())
                    return false;
                try (var in = new URL(server.getJarUrl()).openStream();
                     var out = new FileOutputStream(serverJar, false)) {
                    in.transferTo(out);
                }

                // eula.txt
                var eulaTxt = new FileHandle(server.path("eula.txt").toFile());
                if (!eulaTxt.exists()) {
                    eulaTxt.mkdirs();
                    eulaTxt.createNewFile();
                }
                try (var in = new DelegateStream.Input(new StringReader("eula=true\n"));
                     var out = new FileOutputStream(eulaTxt, false)) {
                    in.transferTo(out);
                }

                pushStatus(Status.online.new Message("Update done"));
                return true;
            } catch (Throwable t) {
                log.error("An error occurred while updating", t);
                return false;
            }
        });
    }

    @SneakyThrows
    public CompletableFuture<?> shutdown(final String reason, final int warnSeconds) {
        return CompletableFuture.supplyAsync(() -> {
            pushStatus(Status.shutting_down.new Message(reason));
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
                log.error("Could not wait for shutdown timeout", e);
            }

            in.println("stop");
            stop.join();
            return null;
        });
    }

    @Override
    @SneakyThrows
    public void closeSelf() {
        if (process == null || getState() != State.Running)
            return;

        pushStatus(Status.offline);

        // try shut down gracefully
        in.println("stop");
        process.onExit().orTimeout(30, TimeUnit.SECONDS).join();

        if (getState() == State.Running) {
            // forcibly close process
            process.destroy();
        }

        runner.eventBus.publish(getServer().getId().toString(), Status.offline);
    }

    @Override
    public String toString() {
        return server.toString() + ": " + switch (getState()) {
            case NotStarted -> "Not started";
            case Exited -> {
                assert process != null;
                yield "Exited (" + process.exitValue() + ")";
            }
            case Running -> "Running";
        }; // todo: include server status fetch
    }

    public enum State implements Named {NotStarted, Exited, Running;}
}
