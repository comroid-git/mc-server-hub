package org.comroid.mcsd.agent;

import lombok.Data;
import lombok.SneakyThrows;
import org.comroid.api.Named;
import org.comroid.api.Startable;
import org.comroid.api.UncheckedCloseable;
import org.comroid.api.io.FileHandle;
import org.comroid.mcsd.core.entity.Server;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.io.PrintStream;

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
        process = Runtime.getRuntime().exec(new String[]{
                "java", "-Xmx%dG".formatted(server.getRamGB()),
                        "-jar", "server.jar", "nogui"},
                new String[0],
                new FileHandle(server.getDirectory(), true));
        in = new PrintStream(process.getOutputStream(), true);
        out = process.getInputStream();
        err = process.getErrorStream();
        //if (Debug.isDebug()) io.redirectToLogger(Log.get("ServerProcess-"+server.getName()));
    }

    @Override
    @SneakyThrows
    public void close() {
        if (getState() != State.Running)
            return;
        if (process != null) {
            var kill = Runtime.getRuntime().exec(new String[]{"kill", "-2", String.valueOf(process.pid())});
            kill.waitFor();
        }
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
