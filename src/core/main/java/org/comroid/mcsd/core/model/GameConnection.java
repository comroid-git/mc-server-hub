package org.comroid.mcsd.core.model;

import io.graversen.minecraft.rcon.RconCommandException;
import io.graversen.minecraft.rcon.RconResponse;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.client.channel.ClientChannel;
import org.comroid.api.DelegateStream;
import org.comroid.api.Event;
import org.comroid.api.info.Log;
import org.comroid.mcsd.core.util.Utils;
import org.comroid.mcsd.core.entity.Server;
import org.intellij.lang.annotations.Language;

import java.io.*;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.comroid.mcsd.core.model.ServerConnection.*;

@Slf4j
public final class GameConnection implements Closeable {
    private final ServerConnection connection;
    public final Server server;
    public final ClientChannel channel;
    public final Event.Bus<String> screen;
    public final DelegateStream.IO io;
    private boolean outputActive;

    public GameConnection(ServerConnection con) throws IOException {
        this.connection = con;
        this.server = con.getServer();
        this.channel = connection.getSession().createExecChannel(server.cmdAttach());

        this.screen = new Event.Bus<>();
        this.io = new DelegateStream.IO()
                .rewireOE(stream -> stream
                        .flatMap(str -> Stream.of(str.split("[\r\n]"))
                                .filter(line -> line.length() > 2))
                        .map(Utils::removeAnsiEscapeSequences)
                        .map(str -> str.replace("<", "&lt;")
                                .replace(">", "&gt;"))
                        .filter(Predicate.not(String::isEmpty))
                        .filter(str -> outputActive || (outputActive = str.startsWith(OutputBeginMarker)))
                        .filter(str -> {
                            if (!str.startsWith(OutputEndMarker))
                                return true;
                            return outputActive = false;
                        }))
                //.redirectToSystem() //debug
                .redirectToLogger(Log.get("screen"))
                .redirectToEventBus(screen);
        io.accept(channel::setIn, channel::setOut, channel::setErr);
        log.info("GameConnection IO Configuration:\n" + io.getAlternateName());

        reconnect();
    }

    @SneakyThrows
    public synchronized void reconnect() {
        channel.open().verify(shTimeout);
        //input.accept(server.cmdAttach());
    }

    @Override
    @SneakyThrows
    public void close() {
        log.warn("ScreenConnection was closed");
        channel.close();
        io.close();
    }

    public String sendCmd(final String cmd, final @Language("RegExp") String endPattern) {
        return connection.getGame().sendCmdRCon(cmd, connection)
                .map(RconResponse::getResponseString)
                .orElseGet(() -> sendCmdScreen(cmd, endPattern).join());
    }

    public synchronized CompletableFuture<String> sendCmdScreen(String cmd, final @Language("RegExp") String endPattern) {
        final var pattern = Pattern.compile(endPattern);
        final var sb = new StringBuilder();
        final Predicate<Event<String>> predicate = e -> pattern.matcher(e.getData()).matches();
        var appender = screen.listen(predicate, e -> sb.append(e.getData()));
        var future = screen.next(predicate).whenComplete((e,t)->appender.close());
        screen.publish(cmd);
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
