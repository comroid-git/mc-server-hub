package org.comroid.mcsd.web.model;

import io.graversen.minecraft.rcon.RconCommandException;
import io.graversen.minecraft.rcon.RconResponse;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.client.channel.ClientChannel;
import org.apache.sshd.common.channel.Channel;
import org.comroid.api.DelegateStream;
import org.comroid.api.Event;
import org.comroid.mcsd.web.entity.Server;
import org.intellij.lang.annotations.Language;

import java.io.*;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static org.comroid.mcsd.web.model.ServerConnection.shTimeout;

@Slf4j
public final class GameConnection implements Closeable {
    private final ServerConnection connection;
    public final Server server;
    public final ClientChannel channel;
    public final Event.Bus<String> input;
    public final Event.Bus<String> output;
    public final Event.Bus<String> error;
    public final DelegateStream.IO io;

    public GameConnection(ServerConnection con) throws IOException {
        this.connection = con;
        this.server = con.getServer();
        this.channel = connection.getSession().createChannel(Channel.CHANNEL_SHELL);

        this.input = new Event.Bus<>();
        this.output = new Event.Bus<>();
        this.error = new Event.Bus<>();

        this.io = new DelegateStream.IO();
        io.log(con.log("screen")).and().attach(
                new DelegateStream.Input(input),
                new DelegateStream.Output(output),
                new DelegateStream.Output(error));
        io.apply(channel::setIn, channel::setOut, channel::setErr);

        input.accept(server.cmdAttach());
        channel.open().verify(shTimeout);
    }

    @Override
    @SneakyThrows
    public void close() {
        log.warn("ScreenConnection was closed");
        channel.close();
        io.close();
    }

    public void sendCmd(String cmd, final @Language("RegExp") String endPattern) {
        if (connection.tryRcon())
            connection.game.sendCmdRCon(cmd, connection);
        else if (endPattern != null) sendCmdScreen(cmd, endPattern).join();
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

    public Optional<RconResponse> sendCmdRCon(final String cmd, ServerConnection connection) {
        if (connection.getRcon() == null)
            return Optional.empty();
        connection.tryRcon();
        return connection.getRcon().minecraftRcon().map(rc -> {
            try {
                return rc.sendSync(() -> cmd);
            } catch (RconCommandException e) {
                log.warn("Internal error occurred when sending command %s to server %s".formatted(cmd, connection.getServer()), e);
                return null;
            }
        });
    }
}
