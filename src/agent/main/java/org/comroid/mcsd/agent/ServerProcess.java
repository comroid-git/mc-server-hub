package org.comroid.mcsd.agent;

import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.comroid.api.*;
import org.comroid.api.io.FileHandle;
import org.comroid.api.os.OS;
import org.comroid.mcsd.agent.discord.DiscordConnection;
import org.comroid.mcsd.api.model.Status;
import org.comroid.mcsd.core.entity.DiscordBot;
import org.comroid.mcsd.core.entity.Server;
import org.comroid.mcsd.core.exception.EntityNotFoundException;
import org.comroid.mcsd.core.repo.ServerRepo;
import org.comroid.util.Debug;
import org.comroid.util.JSON;
import org.comroid.util.MD5;
import org.comroid.util.PathUtil;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.net.URL;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntFunction;

import static org.comroid.mcsd.core.util.ApplicationContextProvider.bean;

@Slf4j
@Getter
@RequiredArgsConstructor
public class ServerProcess extends Event.Bus<String> implements Startable {
    private final AtomicBoolean backupRunning = new AtomicBoolean(false);
    private final AtomicBoolean updateRunning = new AtomicBoolean(false);
    private final AgentRunner runner;
    private final Server server;
    private @Nullable Process process;
    private PrintStream in;
    private InputStream out;
    private InputStream err;
    private DelegateStream.IO oe;
    private DiscordConnection discord;

    public State getState() {
        return process == null
                ? State.NotStarted
                : process.isAlive()
                ? State.Running
                : State.Exited;
    }

    @Override
    @SneakyThrows
    public void start() {
        if (backupRunning.get() || updateRunning.get())
            return;
        if (getState() == State.Running)
            return;
        final var servers = bean(ServerRepo.class);
        servers.setStatus(server.getId(), Status.Starting);

        var exec = PathUtil.findExec("java").orElseThrow();
        process = Runtime.getRuntime().exec(new String[]{
                        exec.getAbsolutePath(),
                        "-Xmx%dG".formatted(server.getRamGB()),
                        "-jar", "server.jar", Debug.isDebug()&& OS.isWindows?"":"nogui"},
                new String[0],
                new FileHandle(server.getDirectory(), true));
        in = new PrintStream(process.getOutputStream(), true);
        out = process.getInputStream();
        err = process.getErrorStream();
        oe = new DelegateStream.IO(DelegateStream.Capability.Output, DelegateStream.Capability.Error)
                .redirectToEventBus(this);
        var executor = Executors.newFixedThreadPool(2);
        addChildren(
                DelegateStream.redirect(out,oe.getOutput(), executor),
                DelegateStream.redirect(err,oe.getError(), executor));
        closed = false;

        waitForOutput("Done\\s\\([\\d.s]+\\)!").thenRun(() -> servers
                .setStatus(server.getId(), server.isMaintenance() ? Status.Maintenance : Status.Online));

        var botConId = server.getDiscordBot();
        if (botConId != null)
            addChildren(discord = new DiscordConnection(this));

        if (Debug.isDebug())
            //oe.redirectToLogger(log);
            oe.redirectToSystem();
    }

    private boolean startBackup() {
        return backupRunning.compareAndSet(false, true);
    }

    @SneakyThrows
    public boolean runBackup() {
        if (!startBackup()) {
            log.warn("A backup on server %s is already running".formatted(server));
            return false;
        }

        in.println("save-off");
        in.println("save-all");

        // wait for save to finish
        waitForOutput("Saved the game").join();

        // do run backup
        var exec = PathUtil.findExec("tar").orElseThrow();
        Process tar = Runtime.getRuntime().exec(new String[]{exec.getAbsolutePath(),
                "--exclude='./cache/**'",
                "--exclude='./libraries/**'",
                "--exclude='./versions/**'",
                "-zcvf",
                    "'"+Paths.get(server.shCon().orElseThrow().getBackupsDir(), server.getName(), "backup.tar.gz")+"'",
                    "'.'"});
        tar.onExit().thenRun(() -> {
            in.println("save-on");
            backupRunning.set(false);
        });
        return true;
    }

    @SneakyThrows
    public boolean isJarUpToDate() {
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

    @SneakyThrows
    public boolean runUpdate(String... args) {
        var flags = String.join("", args);

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
        try (var out = new FileOutputStream(serverProperties,false)) {
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
             var out = new FileOutputStream(serverJar,false)) {
            in.transferTo(out);
        }

        // eula.txt
        var eulaTxt = new FileHandle(server.path("eula.txt").toFile());
        if (!eulaTxt.exists()) {
            eulaTxt.mkdirs();
            eulaTxt.createNewFile();
        }
        try (var in = new DelegateStream.Input(new StringReader("eula=true\n"));
             var out = new FileOutputStream(eulaTxt,false)) {
            in.transferTo(out);
        }
        return true;
    }

    @SneakyThrows
    public CompletableFuture<?> shutdown(final String reason, final int warnSeconds) {
        return CompletableFuture.supplyAsync(() -> {
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

            close();
            return null;
        });
    }

    @Override
    @SneakyThrows
    public void closeSelf() {
        if (process == null || getState() != State.Running)
            return;

        // try shut down gracefully
        in.println("stop");
        process.onExit().orTimeout(30, TimeUnit.SECONDS).join();

        if (getState() == State.Running) {
            // forcibly close process
            process.destroy();
        }

        /*
            var kill = Runtime.getRuntime().exec(new String[]{"kill", "-2", String.valueOf(process.pid())});
            kill.waitFor();
         */
    }

    @Override
    public String toString() {
        return server.toString() + ": " + switch(getState()){
            case NotStarted -> "Not started";
            case Exited -> {
                assert process != null;
                yield "Exited ("+process.exitValue()+")";
            }
            case Running -> "Running";
        }; // todo: include server status fetch
    }

    public CompletableFuture<Event<String>> waitForOutput(@Language("RegExp") String pattern) {
        return listenForOutput(pattern).listen().once();
    }
    public Event.Bus<String> listenForOutput(@Language("RegExp") String pattern) {
        return filter(e->DelegateStream.IO.EventKey_Output.equals(e.getKey()))
                .filterData(str->str.matches(pattern));
    }

    public enum State implements Named { NotStarted, Exited, Running;}
}
