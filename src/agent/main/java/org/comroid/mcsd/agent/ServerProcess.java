package org.comroid.mcsd.agent;

import lombok.Data;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.comroid.api.*;
import org.comroid.api.info.Log;
import org.comroid.api.io.FileHandle;
import org.comroid.api.os.OS;
import org.comroid.mcsd.core.entity.Server;
import org.comroid.mcsd.core.util.ApplicationContextProvider;
import org.comroid.util.Debug;
import org.comroid.util.JSON;
import org.comroid.util.MD5;
import org.comroid.util.PathUtil;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntFunction;
import java.util.logging.Level;
import java.util.stream.IntStream;

import static org.comroid.mcsd.core.util.ApplicationContextProvider.bean;

@Data
@Slf4j
public class ServerProcess extends Event.Bus<String> implements Startable {
    private final AtomicBoolean backupRunning = new AtomicBoolean(false);
    private final AtomicBoolean updateRunning = new AtomicBoolean(false);
    private final Server server;
    private @Nullable Process process;
    private PrintStream in;
    private InputStream out;
    private InputStream err;
    private DelegateStream.IO oe;

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
        var exec = PathUtil.findExec("java").orElseThrow();
        process = Runtime.getRuntime().exec(new String[]{
                        exec.getAbsolutePath(),
                        "-Xmx%dG".formatted(server.getRamGB()),
                        "-jar", "server.jar", "nogui"},
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
        waitForText("Saved the game");

        // do run backup
        var exec = PathUtil.findExec("tar").orElseThrow();
        Process tar = Runtime.getRuntime().exec(new String[]{exec.getAbsolutePath(),
                "--exclude='./cache/**'",
                "--exclude='./libraries/**'",
                "--exclude='./versions/**'",
                "-zcvf", "\"%s.tar.gz\"".formatted(Paths.get(server.shCon().orElseThrow().getBackupsDir(), server.getName())), "\".\""});
        tar.onExit().thenRun(() -> {
            in.println("save-on");
            backupRunning.set(false);
        });
        return true;
    }

    @SneakyThrows
    public boolean isJarUpToDate() {
        var serverJar = new FileHandle(server.path("server.jar").toFile());
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
    public void shutdown(final String reason, int time) {
        final var msg = (IntFunction<String>)t->"Server will shut down for "+reason+" in "+t+" seconds";

        while (time > 0) {
            in.println(msg.apply(time));
            if (time < 10)
                time /= 2;
            else time -= 1;
            Thread.sleep(TimeUnit.SECONDS.toMillis(time));
        }

        close();
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

    private void waitForText(String text) {
        listen().setKey(DelegateStream.IO.EventKey_Output)
                .setPredicate(e -> Objects.requireNonNullElse(e.getData(), "").contains(text))
                .once()
                .join();
    }

    public enum State implements Named { NotStarted, Exited, Running;}
}
