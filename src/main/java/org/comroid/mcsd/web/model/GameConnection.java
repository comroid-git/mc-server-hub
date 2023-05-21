package org.comroid.mcsd.web.model;

import io.graversen.minecraft.rcon.RconCommandException;
import io.graversen.minecraft.rcon.RconResponse;
import lombok.SneakyThrows;
import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.JSchException;
import lombok.extern.slf4j.Slf4j;
import org.comroid.api.DelegateStream;
import org.comroid.api.Event;
import org.comroid.mcsd.web.entity.Server;
import org.intellij.lang.annotations.Language;
import org.slf4j.Logger;
import org.slf4j.event.Level;
import org.springframework.boot.logging.LogLevel;

import java.io.*;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.regex.Pattern;

@Slf4j
public final class GameConnection implements Closeable {
    private final ServerConnection connection;
    public final Server server;
    public final ChannelShell channel;
    public final Event.Bus<String> input;
    public final Event.Bus<String> output;
    public final Event.Bus<String> error;
    public final DelegateStream.IO io;

    public GameConnection(ServerConnection con) throws JSchException {
        this.connection = con;
        this.server = con.getServer();
        this.channel = (ChannelShell) connection.getSession().openChannel("shell");

        this.input = new Event.Bus<>();
        this.output = new Event.Bus<>();
        this.error = new Event.Bus<>();

        this.io = new DelegateStream.IO();
        var mini = con.log("screen");
        io.attach(
                new DelegateStream.Input(input),
                new DelegateStream.Output(output),
                new DelegateStream.Output(error))
                .and().log(mini);
                //.and().system();
        io.accept(channel::setInputStream, channel::setOutputStream, channel::setExtOutputStream);
        mini.info("GameConnection IO Configuration:\n"+io.getAlternateName());

        reconnect();
    }

    @SneakyThrows
    public synchronized void reconnect() {
        channel.connect();
        channel.start();
        input.accept(server.cmdAttach());
    }

    @Override
    @SneakyThrows
    public void close() {
        log.warn("ScreenConnection was closed");
        channel.disconnect();
    }

    public void sendCmd(String cmd, final @Language("RegExp") String endPattern) {
        if (connection.tryRcon())
            connection.sendCmdRCon(cmd);
        else if (endPattern != null)
            sendCmdScreen(cmd, endPattern).join();
        else throw new RuntimeException("Cannot send command " + cmd + " because both RCon and Screen are offline");
    }

    public synchronized CompletableFuture<String> sendCmdScreen(String cmd, final @Language("RegExp") String endPattern) {
        final var pattern = Pattern.compile(endPattern);
        final var sb = new StringBuilder();
        final Predicate<Event<String>> predicate = e -> pattern.matcher(e.getData()).matches();
        var appender = output.listen(predicate, e -> sb.append(e.getData()));
        var future = output.next(predicate).whenComplete((e,t)->appender.close());
        input.accept(cmd);
        return future.thenApply($->sb.toString());
    }
}
