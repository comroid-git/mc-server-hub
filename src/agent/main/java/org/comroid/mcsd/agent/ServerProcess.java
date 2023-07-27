package org.comroid.mcsd.agent;

import lombok.Data;
import lombok.SneakyThrows;
import org.comroid.api.DelegateStream;
import org.comroid.api.Startable;
import org.comroid.api.UncheckedCloseable;
import org.comroid.api.info.Log;
import org.comroid.api.io.FileHandle;
import org.comroid.mcsd.core.entity.Server;
import org.comroid.util.Debug;
import org.jetbrains.annotations.Nullable;

@Data
public class ServerProcess implements Startable, UncheckedCloseable {
    private final Server server;
    private @Nullable DelegateStream.IO io;
    private @Nullable Process process;

    @Override
    @SneakyThrows
    public void start() {
        process = Runtime.getRuntime().exec(new String[]{
                "java", "-Xmx%dG".formatted(server.getRamGB()),
                        "-jar", "server.jar", "nogui"},
                new String[0],
                new FileHandle(server.getDirectory(), true));
        io = new DelegateStream.IO(
                process.getInputStream(),
                process.getOutputStream(),
                null); //todo connect stderr
        if (Debug.isDebug())
            io.redirectToLogger(Log.get("ServerProcess-"+server.getName()));
    }

    @Override
    @SneakyThrows
    public void close() {
        if (process != null)
            Runtime.getRuntime().exec(new String[]{"kill", "-2", String.valueOf(process.pid())});
    }
}
