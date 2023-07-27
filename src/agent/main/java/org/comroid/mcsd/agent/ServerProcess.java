package org.comroid.mcsd.agent;

import lombok.Data;
import lombok.SneakyThrows;
import org.comroid.api.*;
import org.comroid.api.io.FileHandle;
import org.comroid.api.os.OS;
import org.comroid.mcsd.core.entity.Server;
import org.comroid.mcsd.core.util.ApplicationContextProvider;
import org.comroid.util.Debug;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.*;

import static org.comroid.mcsd.core.util.ApplicationContextProvider.bean;

@Data
public class ServerProcess extends Container.Base implements Startable{
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
        oe = new DelegateStream.IO(DelegateStream.Capability.Output, DelegateStream.Capability.Error);
        var executor = Executors.newFixedThreadPool(2);
        addChildren(
                DelegateStream.redirect(out,oe.getOutput(), executor),
                DelegateStream.redirect(err,oe.getError(), executor));

        if (Debug.isDebug())
            //oe.redirectToLogger(Log.get("ServerProcess-"+getId()));
            oe.redirectToSystem();
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
        };
    }

    public enum State implements Named { NotStarted, Exited, Running }
}
