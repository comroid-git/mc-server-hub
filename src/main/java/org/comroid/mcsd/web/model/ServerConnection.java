package org.comroid.mcsd.web.model;

import com.github.rmmccann.minecraft.status.query.MCQuery;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import io.graversen.minecraft.rcon.service.ConnectOptions;
import io.graversen.minecraft.rcon.service.IMinecraftRconService;
import io.graversen.minecraft.rcon.service.MinecraftRconService;
import io.graversen.minecraft.rcon.service.RconDetails;
import lombok.Data;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.dilley.MineStat;
import org.comroid.api.ThrowingFunction;
import org.comroid.mcsd.web.dto.StatusMessage;
import org.comroid.mcsd.web.entity.Server;
import org.comroid.mcsd.web.entity.ShConnection;
import org.comroid.mcsd.web.exception.EntityNotFoundException;
import org.comroid.mcsd.web.repo.ShRepo;
import org.comroid.util.DelegateStream;
import org.jetbrains.annotations.Nullable;
import org.springframework.core.io.ResourceLoader;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.StreamSupport;

import static org.comroid.mcsd.web.util.ApplicationContextProvider.bean;

@Data
@Slf4j
@RequiredArgsConstructor
public class ServerConnection implements Closeable {
    private static final Duration statusCacheLifetime = Duration.ofMinutes(5);
    private static final Map<UUID, StatusMessage> statusCache = new ConcurrentHashMap<>();
    @NonNull
    protected Server server;
    protected Session session;
    protected IMinecraftRconService rcon;

    public static CompletableFuture<StatusMessage> status(final Server srv) {
        log.debug("Getting status of Server " + srv.getName());
        if (statusCache.containsKey(srv.getId())) {
            var entry = statusCache.get(srv.getId());
            if (entry.getTimestamp().plus(statusCacheLifetime).isAfter(Instant.now()))
                return CompletableFuture.completedFuture(entry);
        }
        var host = StreamSupport.stream(bean(ShRepo.class).findAll().spliterator(), false)
                .filter(con -> con.getId().equals(srv.getShConnection()))
                .findFirst()
                .orElseThrow(() -> new EntityNotFoundException(ShConnection.class, "Server " + srv.getName()))
                .getHost();
        var viae = new CompletableFuture[]{
                CompletableFuture.supplyAsync(() -> {
                    var msg = StatusMessage.builder()
                            .serverId(srv.getId());
                    try (var query = new MCQuery(host, srv.getPort())) {
                        var stat = query.fullStat();
                        if (stat != null)
                            msg.status(srv.isMaintenance() ? Server.Status.Maintenance : Server.Status.Online)
                                    .playerCount(stat.getOnlinePlayers())
                                    .playerMax(stat.getMaxPlayers())
                                    .motd(stat.getMOTD().replaceAll("§\\w", ""))
                                    .gameMode(stat.getGameMode())
                                    .players(stat.getPlayerList())
                                    .worldName(stat.getMapName());
                        return msg.build();
                    }
                }),
                CompletableFuture.supplyAsync(() -> {
                    var stat = new MineStat(host, srv.getPort(), 3);
                    return StatusMessage.builder()
                            .serverId(srv.getId())
                            .status(stat.isServerUp() ? srv.isMaintenance() ? Server.Status.Maintenance : Server.Status.Online : Server.Status.Offline)
                            .playerCount(stat.getCurrentPlayers())
                            .playerMax(stat.getMaximumPlayers())
                            .motd(Objects.requireNonNullElse(stat.getStrippedMotd(), "").replaceAll("§\\w", ""))
                            .gameMode(stat.getGameMode())
                            .build();
                })
        };
        var result = new CompletableFuture<StatusMessage>();
        UnaryOperator<StatusMessage> acceptor = msg -> {
            if (statusCache.containsKey(srv.getId()))
                msg = statusCache.get(srv.getId()).combine(msg);
            statusCache.put(srv.getId(), msg);
            return msg;
        };
        Function<Throwable, StatusMessage> logger = e -> {
            log.error("Internal error during status check", e);
            if (Arrays.stream(viae).allMatch(CompletableFuture::isCompletedExceptionally))
                return new StatusMessage(srv.getId());
            return null;
        };
        Consumer<StatusMessage> cacheAcceptor = msg -> {
            if (msg != null && !result.isDone())
                result.complete(msg);
        };
        for (var via : viae)
            //noinspection unchecked
            ((CompletableFuture<StatusMessage>)via).thenApply(acceptor).exceptionally(logger).thenAccept(cacheAcceptor);
        return result;
    }

    private static <T> T facadeCall(Server srv, ThrowingFunction<ServerConnection, T, Exception> call, String errorMessage) {
        try (var con = new ServerConnection(srv)) {
            con.start();
            return call.apply(con);
        } catch (Exception e) {
            log.error(errorMessage, e);
            return null;
        }
    }

    public static @Nullable OutputStream upload(Server srv, String path) {
        return facadeCall(srv, con -> con.uploadFile(path).plus(con),
                "Error opening upload stream to server %s for file %s".formatted(srv.getName(), path));
    }

    public static @Nullable InputStream download(Server srv, String path) {
        return facadeCall(srv, con -> con.downloadFile(path).plus(con),
                "Error opening download stream from server %s from file %s".formatted(srv.getName(), path));
    }

    public static boolean updateProperties(Server srv) {
        return Boolean.TRUE.equals(facadeCall(srv, ServerConnection::updateProperties,
                "Error updating managed properties of server " + srv.getName()));
    }

    public static boolean send(Server srv, String command) {
        try (ServerConnection exec = new ServerConnection(srv) {

            private ChannelExec channel;

            @Override
            protected boolean startConnection() throws Exception {
                this.channel = (ChannelExec) session.openChannel("exec");

                channel.setCommand(command);
                channel.setOutputStream(System.out);
                channel.connect();
                channel.start();

                while (channel.getExitStatus() == -1)
                    //noinspection BusyWait
                    Thread.sleep(10);
                log.info("Command %s for server %s finished with exit code %d".formatted(command, server.getName(), channel.getExitStatus()));
                return true;
            }

            @Override
            public void close() {
                if (channel != null)
                    channel.disconnect();
                super.close();
            }
        }) {
            return exec.start();
        }
    }

    private static int checkAck(InputStream in) throws Exception {
        int b = in.read();
        // b may be 0 for success,
        //          1 for error,
        //          2 for fatal error,
        //          -1
        if (b == 0) return b;
        if (b == -1) return b;

        if (b == 1 || b == 2) {
            StringBuffer sb = new StringBuffer();
            int c;
            do {
                c = in.read();
                sb.append((char) c);
            }
            while (c != '\n');

            if (b == 1) log.error("Error: " + sb);// error
            else log.error("Fatal error: " + sb);// fatal error
        }
        return b;
    }

    public boolean start() {
        try {
            var con = shConnection();

            this.session = bean(JSch.class).getSession(con.getUsername(), con.getHost(), con.getPort());
            session.setPassword(con.getPassword());
            session.setConfig("StrictHostKeyChecking", "no"); // todo This is bad and unsafe
            session.connect();

            this.rcon = new MinecraftRconService(
                    new RconDetails(con.getHost(), server.getPort(), server.getRConPassword()),
                    new ConnectOptions(3, Duration.ofSeconds(1), Duration.ofSeconds(1)));
            rcon.connect();

            return uploadRunScript() && startConnection();
        } catch (Exception e) {
            log.error("Could not start connection", e);
            return false;
        }
    }

    private boolean updateProperties() {
        final var fileName = "server.properties";
        final var path = server.getDirectory() + '/' + fileName;

        // download & update & upload properties
        try (var in = downloadFile(path)) {
            var prop = updateProperties(server, in);
            try (var out = uploadFile(path)) {
                prop.store(out, "Managed Server Properties by MCSD");
            }
        } catch (Exception e) {
            log.error("Error uploading managed server.properties", e);
            return false;
        }
        log.info("Uploaded managed properties of server " + server.getName());
        return true;
    }

    private Properties updateProperties(Server srv, InputStream input) throws IOException {
        var prop = new Properties();
        prop.load(input);

        prop.setProperty("server-port", String.valueOf(srv.getPort()));
        prop.setProperty("max-players", String.valueOf(srv.getMaxPlayers()));

        // query
        prop.setProperty("enable-query", String.valueOf(true));
        prop.setProperty("query.port", String.valueOf(srv.getQueryPort()));

        // rcon
        prop.setProperty("enable-rcon", String.valueOf(!srv.getRConPassword().isBlank()));
        prop.setProperty("rcon.port", String.valueOf(srv.getRConPort()));
        prop.setProperty("rcon.password", srv.getRConPassword());

        return prop;
    }

    private boolean uploadRunScript() {
        var lfile = "mcsd.sh";
        var rfile = server.getDirectory() + '/' + lfile;
        var res = bean(ResourceLoader.class).getResource(lfile);
        try (var source = res.getInputStream();
             var target = uploadFile(rfile)) {
            source.transferTo(target);
            return true;
        } catch (Exception e) {
            log.error("Unable to upload runscript for Server " + server.getName(), e);
            return false;
        }
    }

    public DelegateStream.Output uploadFile(final String path) throws Exception {
        var sftp = (ChannelSftp) session.openChannel("sftp");
        sftp.connect();
        return new DelegateStream.Output(sftp.put(path), sftp::disconnect);
    }

    public DelegateStream.Input downloadFile(final String path) throws Exception {
        var sftp = (ChannelSftp) session.openChannel("sftp");
        sftp.connect();
        return new DelegateStream.Input(sftp.get(path), sftp::disconnect);
    }

    public ShConnection shConnection() {
        return bean(ShRepo.class)
                .findById(server.getShConnection())
                .orElseThrow(() -> new EntityNotFoundException(ShConnection.class, server.getShConnection()));
    }

    @Override
    public void close() {
        if (session != null)
            session.disconnect();
        if (rcon != null)
            rcon.disconnect();
    }

    protected boolean startConnection() throws Exception {
        return true;
    }
}
