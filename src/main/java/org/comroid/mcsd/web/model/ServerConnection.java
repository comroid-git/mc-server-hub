package org.comroid.mcsd.web.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.rmmccann.minecraft.status.query.MCQuery;
import com.jcraft.jsch.*;
import io.graversen.minecraft.rcon.service.IMinecraftRconService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import me.dilley.MineStat;
import org.comroid.mcsd.web.dto.StatusMessage;
import org.comroid.mcsd.web.entity.Server;
import org.comroid.mcsd.web.entity.ShConnection;
import org.comroid.mcsd.web.exception.EntityNotFoundException;
import org.comroid.mcsd.web.repo.ShRepo;
import org.comroid.util.Delegate;
import org.springframework.core.io.ResourceLoader;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
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
public class ServerConnection implements Closeable {
    private static final Object resourceLock = new Object();
    private final Map<UUID, StatusMessage> statusCache = new ConcurrentHashMap<>();
    private final Duration statusCacheLifetime = Duration.ofMinutes(5);
    protected final Server server;
    protected Session session;
    protected IMinecraftRconService rcon;

    public ServerConnection(Server server) {
        this.server = server;
        try {
            var con = shConnection();

            this.session = bean(JSch.class).getSession(con.getUsername(), con.getHost(), con.getPort());
            session.setPassword(con.getPassword());
            session.setConfig("StrictHostKeyChecking", "no"); // todo This is bad and unsafe
            session.connect();

            /*
            this.rcon = new MinecraftRconService(
                    new RconDetails(con.getHost(), server.getPort(), server.getRConPassword()),
                    new ConnectOptions(3, Duration.ofSeconds(1), Duration.ofSeconds(1)));
            rcon.connect();
             */
        } catch (Exception e) {
            log.error("Could not start connection to server " + server.getName(), e);
        }
    }

    public void cron() {
        log.info("Running cronjob for Server %s".formatted(server.getName()));
        var con = server.getConnection();

        // upload runscript + data
        if (!con.uploadRunScript())
            log.warn("Unable to upload runscript to server " + server.getName());

        // manage server.properties file
        if (!con.updateProperties())
            log.warn("Unable to update server properties for server " + server.getName());

        // is it not offline?
        if (con.status().join().getStatus() != Server.Status.Offline) {
            log.info("Server %s did not need to be started".formatted(server.getName()));
            return;
        }

        // start server
        if (!con.sendSh(server.cmdStart()))
            log.warn("Auto-Starting server %s did not finish successfully".formatted(server.getName()));
    }

    public CompletableFuture<StatusMessage> status() {
        log.debug("Getting status of Server " + server.getName());
        if (statusCache.containsKey(server.getId())) {
            var entry = statusCache.get(server.getId());
            if (entry.getTimestamp().plus(statusCacheLifetime).isAfter(Instant.now()))
                return CompletableFuture.completedFuture(entry);
        }
        var host = StreamSupport.stream(bean(ShRepo.class).findAll().spliterator(), false)
                .filter(con -> con.getId().equals(server.getShConnection()))
                .findFirst()
                .orElseThrow(() -> new EntityNotFoundException(ShConnection.class, "Server " + server.getName()))
                .getHost();
        var viae = new CompletableFuture[]{
                CompletableFuture.supplyAsync(() -> {
                    var msg = StatusMessage.builder()
                            .serverId(server.getId());
                    try (var query = new MCQuery(host, server.getQueryPort())) {
                        var stat = query.fullStat();
                        if (stat != null)
                            msg.status(server.isMaintenance() ? Server.Status.Maintenance : Server.Status.Online)
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
                    var stat = new MineStat(host, server.getPort(), 3);
                    return StatusMessage.builder()
                            .serverId(server.getId())
                            .status(stat.isServerUp() ? server.isMaintenance() ? Server.Status.Maintenance : Server.Status.Online : Server.Status.Offline)
                            .playerCount(stat.getCurrentPlayers())
                            .playerMax(stat.getMaximumPlayers())
                            .motd(Objects.requireNonNullElse(stat.getStrippedMotd(), "").replaceAll("§\\w", ""))
                            .gameMode(stat.getGameMode())
                            .build();
                })
        };
        var result = new CompletableFuture<StatusMessage>();
        UnaryOperator<StatusMessage> acceptor = msg -> {
            if (statusCache.containsKey(server.getId()))
                msg = statusCache.get(server.getId()).combine(msg);
            statusCache.put(server.getId(), msg);
            return msg;
        };
        Function<Throwable, StatusMessage> logger = e -> {
            log.error("Internal error during status check", e);
            if (Arrays.stream(viae).allMatch(CompletableFuture::isCompletedExceptionally))
                return new StatusMessage(server.getId());
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


    public boolean updateProperties() {
        final var fileName = "server.properties";
        final var path = server.getDirectory() + '/' + fileName;

        // download & update & upload properties
        try (var in = downloadFile(path)) {
            var prop = updateProperties(in);
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

    private Properties updateProperties(InputStream input) throws IOException {
        var prop = new Properties();
        prop.load(input);

        prop.setProperty("server-port", String.valueOf(server.getPort()));
        prop.setProperty("max-players", String.valueOf(server.getMaxPlayers()));
        prop.setProperty("white-list", String.valueOf(server.isMaintenance()));

        // query
        prop.setProperty("enable-query", String.valueOf(true));
        prop.setProperty("query.port", String.valueOf(server.getQueryPort()));

        // rcon
        prop.setProperty("enable-rcon", String.valueOf(server.getRConPassword() != null && !server.getRConPassword().isBlank()));
        prop.setProperty("rcon.port", String.valueOf(server.getRConPort()));
        prop.setProperty("rcon.password", Objects.requireNonNullElse(server.getRConPassword(), ""));

        return prop;
    }

    public boolean uploadRunScript() {
        synchronized (resourceLock) {
            var script = "mcsd.sh";
            var data = "mcsd-unit.properties";
            var prefix = server.getDirectory() + '/';
            var res = bean(ResourceLoader.class).getResource(script);
            try {
                // upload runscript
                try (var scriptIn = res.getInputStream();
                     var scriptOut = uploadFile(prefix + script)) {
                    scriptIn.transferTo(scriptOut);
                    log.info("Uploaded runscript to Server " + server.getName());
                }

                // upload unit info
                try (var dataOut = uploadFile(prefix + data)) {
                    var fields = bean(ObjectMapper.class).valueToTree(server).fields();
                    var prop = new Properties();
                    while (fields.hasNext()) {
                        var field = fields.next();
                        prop.put(field.getKey(), field.getValue().asText());
                    }
                    prop.put("backupDir", shConnection().getBackupsDir());
                    prop.store(dataOut, "MCSD Server Unit Information " + Instant.now());
                    log.info("Uploaded runscript data to Server " + server.getName());
                }

                return true;
            } catch (Exception e) {
                log.error("Unable to upload runscript for Server " + server.getName(), e);
                return false;
            }
        }
    }

    public Delegate.Output uploadFile(final String path) throws Exception {
        var sftp = (ChannelSftp) session.openChannel("sftp");
        sftp.connect();
        return new Delegate.Output(sftp.put(path), sftp::disconnect);
    }

    public Delegate.Input downloadFile(final String path) throws Exception {
        var sftp = (ChannelSftp) session.openChannel("sftp");
        sftp.connect();
        return new Delegate.Input(sftp.get(path), sftp::disconnect);
    }

    public boolean sendSh(String cmd) {
        ChannelExec exec = null;
        try {
            exec = (ChannelExec) session.openChannel("exec");
            exec.setCommand(cmd);
            //exec.setOutputStream(System.out);
            //exec.setErrStream(System.err);

            exec.connect();
            exec.start();

            while (exec.getExitStatus() == -1)
                //noinspection BusyWait
                Thread.sleep(10);

            return true;
        } catch (Exception e) {
            log.error("Could not send command to server " + server.getName(), e);
        } finally {
            if (exec != null)
                exec.disconnect();
        }
        return false;
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
}
