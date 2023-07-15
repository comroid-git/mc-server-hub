package org.comroid.mcsd.core.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.rmmccann.minecraft.status.query.MCQuery;
import io.graversen.minecraft.rcon.service.ConnectOptions;
import io.graversen.minecraft.rcon.service.IMinecraftRconService;
import io.graversen.minecraft.rcon.service.MinecraftRconService;
import io.graversen.minecraft.rcon.service.RconDetails;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.Synchronized;
import me.dilley.MineStat;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.channel.ClientChannelEvent;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.channel.Channel;
import org.apache.sshd.sftp.client.SftpClient;
import org.apache.sshd.sftp.client.impl.DefaultSftpClientFactory;
import org.comroid.api.DelegateStream;
import org.comroid.api.ThrowingFunction;
import org.comroid.api.info.Log;
import org.comroid.mcsd.core.dto.StatusMessage;
import org.comroid.mcsd.core.entity.ShConnection;
import org.comroid.mcsd.core.exception.EntityNotFoundException;
import org.comroid.mcsd.core.repo.DiscordBotRepo;
import org.comroid.mcsd.core.repo.ServerRepo;
import org.comroid.mcsd.core.repo.ShRepo;
import org.comroid.mcsd.core.entity.Server;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.StreamSupport;

import static org.comroid.mcsd.core.util.ApplicationContextProvider.bean;

@Getter
public final class ServerConnection implements Closeable, ServerHolder {
    @Language("html")
    public static final String br = "<br/>";
    public static final String OutputBeginMarker = "~~>";
    public static final String OutputEndMarker = "<~>";
    public static final String RunScript = "mcsd.sh";
    public static final String UnitFile = "unit.properties";
    private static final Map<UUID, ServerConnection> cache = new ConcurrentHashMap<>();
    private static final Map<UUID, StatusMessage> statusCache = new ConcurrentHashMap<>();
    private static final Map<String, Object> locks = new ConcurrentHashMap<>();
    private static final Duration statusCacheLifetime = Duration.ofMinutes(1);
    private static final Duration statusTimeout = Duration.ofSeconds(10);
    static final Duration shTimeout = Duration.ofMinutes(2);
    private static final Resource runscript = bean(ResourceLoader.class).getResource("classpath:/"+ServerConnection.RunScript);
    private final ShConnection con;
    @JsonIgnore @Getter(onMethod_ = @JsonIgnore)
    private final Logger log;
    @JsonIgnore @Getter(onMethod_ = @JsonIgnore)
    private final Server server;
    @JsonIgnore @Getter(onMethod_ = @JsonIgnore)
    private final GameConnection game;
    @JsonIgnore @Getter(onMethod_ = @JsonIgnore)
    private final ClientSession session;
    @JsonIgnore @Getter(onMethod_ = @JsonIgnore)
    private final SftpClient sftp;
    @JsonIgnore @Getter(onMethod_ = @JsonIgnore)
    private final IMinecraftRconService rcon;
    @JsonIgnore @Getter(onMethod_ = @JsonIgnore) @Nullable
    private final DiscordConnection discord;
    private final AtomicBoolean backupRunning = new AtomicBoolean(false);

    private ServerConnection(Server server) throws IOException {
        this.server = server;
        this.con = shConnection();
        this.log = Log.get(con.toString());

        this.session = bean(SshClient.class)
                .connect(con.getUsername(), con.getHost(), con.getPort())
                .verify(shTimeout)
                .getSession();

        session.addPasswordIdentity(con.getPassword());
        session.auth().verify(shTimeout);

        this.sftp = new DefaultSftpClientFactory().createSftpClient(session);

        if (!uploadRunScript() | !uploadProperties())
            throw new RuntimeException("Could not connect to "+server+"; unable to upload runscript");

        this.game = new GameConnection(this);
        this.rcon = new MinecraftRconService(
                new RconDetails(con.getHost(), server.getRConPort(), server.getRConPassword()),
                new ConnectOptions(Integer.MAX_VALUE, Duration.ofSeconds(3), Duration.ofMinutes(5)));
        this.discord = Optional.ofNullable(server.getDiscordConnection())
                .flatMap(id -> bean(DiscordBotRepo.class).findById(id))
                .map(info -> new DiscordConnection(this, info))
                .orElse(null);
    }

    public static ServerConnection getInstance(final Server srv) {
        return cache.computeIfAbsent(srv.getId(), ThrowingFunction.rethrowing($ -> new ServerConnection(srv)));
    }

    @Synchronized("rcon")
    boolean tryRcon() {
        try {
            if (!rcon.connectBlocking(statusTimeout))
                log.warn("RCon handshake timed out for " + server);
        } catch (Exception e) {
            log.error("Unable to connect RCon to " + server, e);
        }
        return rcon.isConnected();
    }

    public void cron() {
        log.info("Running cronjob for %s".formatted(server));
        var con = server.con();

        // upload runscript + data
        //if (!con.uploadRunScript())
        //    log.warn("Unable to upload runscript to %s".formatted(server));

        // manage server.properties file
        //if (!con.uploadProperties())
        //    log.warn("Unable to upload managed server properties to %s".formatted(server));

        // is it offline?
        if (con.status().join().getStatus() == Server.Status.Offline) {
            // then start server
            if (!con.sendSh(server.cmdStart()))
                log.warn("Auto-Starting %s did not finish successfully".formatted(server));
        } else {
            log.debug("%s did not need to be started".formatted(server));
        }

        // validate RCON connection works
        tryRcon();
    }

    @SneakyThrows
    public CompletableFuture<StatusMessage> status() {
        log.debug("Getting status of Server %s".formatted(server));
        var host = StreamSupport.stream(bean(ShRepo.class).findAll().spliterator(), false)
                .filter(con -> con.getId().equals(server.getShConnection()))
                .findFirst()
                .orElseThrow(() -> new EntityNotFoundException(ShConnection.class, server))
                .getHost();
        return CompletableFuture.supplyAsync(() -> Objects.requireNonNull(statusCache.computeIfPresent(server.getId(), (k, v) -> {
                    if (v.getTimestamp().plus(statusCacheLifetime).isBefore(Instant.now()))
                        return null;
                    return v;
                }), "Status cache outdated"))
                .exceptionally(t -> {
                    log.debug("Unable to get server status from cache ["+t.getMessage()+"], using Query...");
                    log.trace("Exception was", t);
                    try (var query = new MCQuery(host, server.getQueryPort())) {
                        var stat = query.fullStat();
                        return statusCache.compute(server.getId(), (id, it) -> it == null ? new StatusMessage(id) : it)
                                .withRcon(rcon.isConnected() ? Server.Status.Online : Server.Status.Offline)
                                .withSsh(game.channel.isOpen() ? Server.Status.Online : Server.Status.Offline)
                                .withStatus(server.isMaintenance() ? Server.Status.Maintenance : Server.Status.Online)
                                .withPlayerCount(stat.getOnlinePlayers())
                                .withPlayerMax(stat.getMaxPlayers())
                                .withMotd(stat.getMOTD().replaceAll("§\\w", ""))
                                .withGameMode(stat.getGameMode())
                                .withPlayers(stat.getPlayerList())
                                .withWorldName(stat.getMapName());
                    }
                })
                .exceptionally(t -> {
                    log.debug("Unable to get server status using Query ["+t.getMessage()+"], using MineStat...");
                    log.trace("Exception was", t);
                    var stat = new MineStat(host, server.getPort());
                    return statusCache.compute(server.getId(), (id, it) -> it == null ? new StatusMessage(id) : it)
                            .withRcon(rcon.isConnected() ? Server.Status.Online : Server.Status.Offline)
                            .withSsh(game.channel.isOpen() ? Server.Status.Online : Server.Status.Offline)
                            .withStatus(stat.isServerUp() ? server.isMaintenance() ? Server.Status.Maintenance : Server.Status.Online : Server.Status.Offline)
                            .withPlayerCount(stat.getCurrentPlayers())
                            .withPlayerMax(stat.getMaximumPlayers())
                            .withMotd(Objects.requireNonNullElse(stat.getStrippedMotd(), "").replaceAll("§\\w", ""))
                            .withGameMode(stat.getGameMode());
                })
                .orTimeout(statusTimeout.toSeconds(), TimeUnit.SECONDS)
                .exceptionally(t -> {
                    log.warn("Unable to get server status ["+t.getMessage()+"]");
                    log.trace("Exception was", t);
                    return statusCache.compute(server.getId(), (id, it) -> it == null ? new StatusMessage(id) : it)
                            .withRcon(rcon.isConnected() ? Server.Status.Online : Server.Status.Offline)
                            .withSsh(game.channel.isOpen() ? Server.Status.Online : Server.Status.Offline);
                })
                .thenApply(msg -> {
                    statusCache.put(server.getId(), msg);
                    return msg;
                })
                .orTimeout(statusTimeout.toSeconds() + 1, TimeUnit.SECONDS);
    }

    public boolean uploadProperties() {
        final var fileName = "server.properties";
        final var path = server.getDirectory() + '/' + fileName;

        // download & update & upload properties
        try (var in = downloadFile(path)) {
            var prop = updateProperties(in);
            try (var out = uploadFile(path)) {
                prop.store(out, "Managed Server Properties by MCSD");
            }
        } catch (Exception e) {
            log.error("Error uploading managed server.properties for server " + server, e);
            return false;
        }
        log.debug("Uploaded managed properties of server " + server);
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
        var prefix = server.getDirectory() + '/';
        try {
            // upload runscript
            try (var scriptIn = runscript.getInputStream();
                 var scriptOut = uploadFile(prefix + RunScript)) {
                scriptIn.transferTo(scriptOut);
                log.debug("Uploaded runscript to Server " + server);
            }

            // upload unit info
            try (var dataOut = uploadFile(prefix + UnitFile)) {
                var fields = bean(ObjectMapper.class).valueToTree(server).fields();
                var prop = new Properties();
                while (fields.hasNext()) {
                    var field = fields.next();
                    prop.put(field.getKey(), field.getValue().asText());
                }
                var con = shConnection();
                prop.put("host", con.getHost());
                prop.put("backupDir", con.getBackupsDir() + '/' + server.getUnitName());
                prop.store(dataOut, "MCSD Server Unit Information");
                log.debug("Uploaded runscript data to Server " + server);
            }

            return true;
        } catch (Exception e) {
            log.error("Unable to upload runscript for Server " + server, e);
            return false;
        }
    }

    public boolean runBackup() {
        if (!startBackup()) {
            log.warn("A backup on server %s is already running".formatted(server));
            return false;
        }

        if (rcon.isConnected()) {
            try {
                var sOff = game.sendCmdRCon("save-off", this);
                var sAll = game.sendCmdRCon("save-all", this);
                if (sOff.isEmpty() || sAll.isEmpty()) {
                    log.error("Could not run backup on server %s because save-off and save-all failed".formatted(server));
                    return false;
                }
                if (!doBackup()) {
                    log.error("Could not run backup on server %s using RCon".formatted(server));
                    return false;
                }
            } finally {
                var sOn = game.sendCmdRCon("save-on", this);
                if (sOn.isEmpty())
                    log.error("Could not enable autosave after backup for " + server);
            }
            backupRunning.set(false);
            return true;
        } else {
            try {
                game.sendCmd("save-off", "^.*(Automatic saving is now disabled).*$");
                game.sendCmd("save-all", "^.*(Saved the game).*$");
                if (!doBackup()) {
                    log.error("Could not run backup on server %s using screen".formatted(server));
                    return false;
                }
            } finally {
                game.sendCmd("save-on", "^.*(Automatic saving is now enabled).*$");
            }
            backupRunning.set(false);
            return true;
        }
    }

    private boolean startBackup() {
        return backupRunning.compareAndSet(false, true);
    }

    private boolean doBackup() {
        if (!sendSh(server.cmdBackup())) {
            log.error("Backup for server %s failed".formatted(server));
            return false;
        }
        server.setLastBackup(Instant.now());
        bean(ServerRepo.class).save(server);
        log.info("Backup for server %s finished".formatted(server));
        return true;
    }

    public boolean runUpdate() {
        if (!uploadRunScript())
            log.warn("Could not upload runscript when trying to update " + server);
        if (!uploadProperties())
            log.warn("Could not upload properties when trying to update " + server);
        if (sendSh(server.cmdUpdate())) {
            return true;
        }
        log.error("Could not update " + server);
        return false;
    }

    @SneakyThrows
    public boolean startServer() {
        return sendSh(server.cmdStart());
    }

    public boolean stopServer() {
        if (!sendSh(server.cmdDisable()))
            log.warn("Could not disable server restarts when trying to stop " + server);
        try {
            game.sendCmd("stop", "^.*" + ServerConnection.OutputEndMarker + ".*$");
            return true;
        } catch (Throwable e) {
            log.error("Could not stop " + server, e);
            return false;
        }
    }

    @SneakyThrows
    public OutputStream uploadFile(final String path) {
        synchronized (lock(':' + path)) {
            return sftp.write(path);
        }
    }

    @SneakyThrows
    public InputStream downloadFile(final String path) {
        synchronized (lock(':' + path)) {
            return sftp.read(path);
        }
    }

    @SneakyThrows
    public boolean sendSh(@Language("sh") String cmd) {
        synchronized (lock('$' + cmd)) {
            try (var io = new DelegateStream.IO().redirectToLogger(log);
                 var exec = session.createChannel(Channel.CHANNEL_EXEC);
                 var writer = new PrintWriter(exec.getInvertedIn())) {
                io.accept(null, exec::setOut, exec::setErr);

                writer.println(cmd);

                exec.open().verify(shTimeout);
                exec.waitFor(EnumSet.of(ClientChannelEvent.CLOSED), shTimeout);

                return true;
            } catch (Throwable t) {
                log.debug("Could not send command '" + cmd + "' to " + server);
                log.trace("Exception was", t);
                return false;
            }
        }
    }

    @Override
    public void close() throws IOException {
        if (rcon != null)
            rcon.disconnect();
        if (game != null)
            game.close();
        if (sftp != null)
            sftp.close();
        if (session != null)
            session.close();
    }

    @Override
    public String toString() {
        return "%s (%s)".formatted(con.getHost(), server.getUnitName());
    }

    private ShConnection shConnection() {
        return bean(ShRepo.class)
                .findById(server.getShConnection())
                .orElseThrow(() -> new EntityNotFoundException(ShConnection.class, server.getShConnection()));
    }

    private Object lock(String route) {
        return locks.computeIfAbsent(shConnection().getHost() + route, $ -> new Object());
    }
}
