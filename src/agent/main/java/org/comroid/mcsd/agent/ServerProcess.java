package org.comroid.mcsd.agent;

import lombok.Data;
import lombok.SneakyThrows;
import org.comroid.api.Named;
import org.comroid.api.Startable;
import org.comroid.api.UncheckedCloseable;
import org.comroid.api.io.FileHandle;
import org.comroid.api.os.OS;
import org.comroid.mcsd.core.entity.Server;
import org.comroid.util.Debug;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Data
public class ServerProcess implements Startable, UncheckedCloseable {
    private final Server server;
    private @Nullable Process process;
    private PrintStream in;
    private InputStream out;
    private InputStream err;

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
        if (getState() == State.Running)
            return;
        var exec = Arrays.stream(System.getenv("PATH").split(File.pathSeparator))
                .map(p->Path.of(p,"java"+(OS.isWindows?".exe":"")).toFile())
                .filter(File::exists)
                .findAny()
                .map(File::getAbsolutePath)
                .orElseThrow();
        process = Runtime.getRuntime().exec(new String[]{
                exec, "-Xmx%dG".formatted(server.getRamGB()),
                        "-jar", "server.jar", "nogui"},
                new String[0],
                new FileHandle(server.getDirectory(), true));
        in = new PrintStream(process.getOutputStream(), true);
        out = process.getInputStream();
        err = process.getErrorStream();

        //if (Debug.isDebug())
            //io.redirectToLogger(Log.get("ServerProcess-"+getId()));
            //io.redirectToSystem();
    }

    @Override
    @SneakyThrows
    public void close() {
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
        };
    }

    public enum State implements Named { NotStarted, Exited, Running }
}
