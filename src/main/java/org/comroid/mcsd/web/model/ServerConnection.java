package org.comroid.mcsd.web.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.rmmccann.minecraft.status.query.MCQuery;
import com.jcraft.jsch.*;
import io.graversen.minecraft.rcon.RconCommandException;
import io.graversen.minecraft.rcon.RconResponse;
import io.graversen.minecraft.rcon.service.ConnectOptions;
import io.graversen.minecraft.rcon.service.IMinecraftRconService;
import io.graversen.minecraft.rcon.service.MinecraftRconService;
import io.graversen.minecraft.rcon.service.RconDetails;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;
import me.dilley.MineStat;
import org.comroid.api.BitmaskAttribute;
import org.comroid.api.DelegateStream;
import org.comroid.api.ThrowingFunction;
import org.comroid.mcsd.web.dto.StatusMessage;
import org.comroid.mcsd.web.entity.Server;
import org.comroid.mcsd.web.entity.ShConnection;
import org.comroid.mcsd.web.exception.EntityNotFoundException;
import org.comroid.mcsd.web.exception.StatusCode;
import org.comroid.mcsd.web.repo.ShRepo;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.Nullable;
import org.slf4j.event.Level;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpStatus;

import java.io.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

import static org.comroid.mcsd.web.model.ServerConnection.BackupMethod.RCon;
import static org.comroid.mcsd.web.model.ServerConnection.BackupMethod.Screen;
import static org.comroid.mcsd.web.util.ApplicationContextProvider.bean;

@Slf4j
@Getter
public final class ServerConnection implements Closeable, ServerHolder {
    @Language("html")
    public static final String br = "<br/>";
    public static final String OutputMarker = "################ Output Began ################";
    public static final String EndMarker = "################ Output Ended ################";
    public static final String RunScript = "mcsd.sh";
    public static final String UnitFile = "unit.properties";
    private static final Map<UUID, ServerConnection> cache = new ConcurrentHashMap<>();
    private static final Map<UUID, StatusMessage> statusCache = new ConcurrentHashMap<>();
    private static final Map<String, Object> locks = new ConcurrentHashMap<>();
    private static final Duration statusCacheLifetime = Duration.ofMinutes(1);
    private static final Duration statusTimeout = Duration.ofSeconds(10);
    private static final Resource runscript = bean(ResourceLoader.class).getResource("classpath:/mcsd.sh");
    private final ShConnection con;
    private final Server server;
    private final Session ssh;
    private final IMinecraftRconService rcon;
    private final AtomicBoolean backupRunning = new AtomicBoolean(false);

    private ServerConnection(Server server) throws JSchException {
        this.server = server;
        this.con = shConnection();
        this.ssh = bean(JSch.class).getSession(con.getUsername(), con.getHost(), con.getPort());
        ssh.setPassword(con.getPassword());
        ssh.setConfig("StrictHostKeyChecking", "no"); // todo This is bad and unsafe
        ssh.connect();

        this.rcon = new MinecraftRconService(
                new RconDetails(con.getHost(), server.getRConPort(), server.getRConPassword()),
                new ConnectOptions(Integer.MAX_VALUE, Duration.ofSeconds(3), Duration.ofMinutes(5)));
    }

    public static ServerConnection getInstance(final Server srv) {
        return cache.computeIfAbsent(srv.getId(), ThrowingFunction.rethrowing($ -> new ServerConnection(srv), RuntimeException::new));
    }

    @Synchronized("rcon")
    private void tryRcon() {
        try {
            if (!rcon.connectBlocking(statusTimeout))
                log.warn("RCon handshake timed out for " + server);
        } catch (Exception e) {
            log.error("Unable to connect RCon to " + server, e);
        }
    }

    public void cron() {
        log.info("Running cronjob for %s".formatted(server));
        var con = server.getConnection();

        // upload runscript + data
        if (!con.uploadRunScript())
            log.warn("Unable to upload runscript to %s".formatted(server));

        // manage server.properties file
        if (!con.uploadProperties())
            log.warn("Unable to upload managed server properties to %s".formatted(server));

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
                    log.trace("Unable to get server status from cache, using Query...", t);
                    try (var query = new MCQuery(host, server.getQueryPort())) {
                        var stat = query.fullStat();
                        return statusCache.compute(server.getId(), (id, it) -> it == null ? new StatusMessage(id) : it)
                                .withRcon(rcon.isConnected() ? Server.Status.Online : Server.Status.Offline)
                                .withSsh(ssh.isConnected() ? Server.Status.Online : Server.Status.Offline)
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
                    log.trace("Unable to get server status using Query, using MineStat...", t);
                    var stat = new MineStat(host, server.getPort());
                    return statusCache.compute(server.getId(), (id, it) -> it == null ? new StatusMessage(id) : it)
                            .withRcon(rcon.isConnected() ? Server.Status.Online : Server.Status.Offline)
                            .withSsh(ssh.isConnected() ? Server.Status.Online : Server.Status.Offline)
                            .withStatus(stat.isServerUp() ? server.isMaintenance() ? Server.Status.Maintenance : Server.Status.Online : Server.Status.Offline)
                            .withPlayerCount(stat.getCurrentPlayers())
                            .withPlayerMax(stat.getMaximumPlayers())
                            .withMotd(Objects.requireNonNullElse(stat.getStrippedMotd(), "").replaceAll("§\\w", ""))
                            .withGameMode(stat.getGameMode());
                })
                .orTimeout(statusTimeout.toSeconds(), TimeUnit.SECONDS)
                .exceptionally(t -> {
                    log.warn("Unable to get server status", t);
                    return statusCache.compute(server.getId(), (id, it) -> it == null ? new StatusMessage(id) : it)
                            .withRcon(rcon.isConnected() ? Server.Status.Online : Server.Status.Offline)
                            .withSsh(ssh.isConnected() ? Server.Status.Online : Server.Status.Offline);
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

    public int runBackup(BackupMethod method) {
        switch (method) {
            case Evaluate -> {
                var mask = 0;
                if (rcon != null && rcon.isConnected())
                    mask |= RCon.getAsInt();
                if (ssh != null && ssh.isConnected())
                    mask |= Screen.getAsInt();
                return mask;
            }
            case RCon -> {
                if (runBackupRCon())
                    return 0;
                throw new StatusCode(HttpStatus.SERVICE_UNAVAILABLE, "RCon is not available right now for " + server);
            }
            case Screen -> {
                if (runBackupScreen())
                    return 0;
                throw new StatusCode(HttpStatus.SERVICE_UNAVAILABLE, "Could not run backup through screen on " + server);
            }
            default -> throw new StatusCode(HttpStatus.BAD_REQUEST, "Unexpected backup method " + method);
        }
    }

    public synchronized boolean runBackupRCon() {
        if (!startBackup()) {
            log.warn("A backup on server %s is already running".formatted(server));
            return false;
        }

        try {
            var sOff = sendCmdRCon("save-off");
            var sAll = sendCmdRCon("save-all");
            if (sOff.isEmpty() || sAll.isEmpty()) {
                log.error("Could not run backup on server %s because save-off and save-all failed".formatted(server));
                return false;
            }
            if (!doBackup()) {
                log.error("Could not run backup on server %s using RCon".formatted(server));
                return false;
            }
        } finally {
            var sOn = sendCmdRCon("save-on");
            if (sOn.isEmpty())
                log.error("Could not enable autosave after backup for " + server);
        }
        backupRunning.set(false);
        return true;
    }

    @SneakyThrows
    public synchronized boolean runBackupScreen() {
        if (!startBackup()) {
            log.warn("A backup on server %s is already running".formatted(server));
            return false;
        }

        try (var screen = attach()) {
            try {
                screen.exec("save-off", "^.*(Automatic saving is now disabled).*$");
                screen.exec("save-all", "^.*(Saved the game).*$");
                if (!doBackup()) {
                    log.error("Could not run backup on server %s using screen".formatted(server));
                    return false;
                }
            } finally {
                screen.exec("save-on", "^.*(Automatic saving is now enabled).*$");
            }
            backupRunning.set(false);
            return true;
        }
    }

    private boolean startBackup() {
        return backupRunning.compareAndSet(false, true);
    }

    private boolean doBackup() {
        OutputStream bufProgress = null;
        var bufSize = new StringWriter();
        if (!sendSh(server.wrapCmd("./mcsd.sh backupSize", true), new DelegateStream.Output(bufSize), new DelegateStream.Output(bufSize)))
            log.error("Unable to obtain backup size");
        else {
            final long size = Long.parseLong(bufSize.toString());
            bufProgress = new OutputStream() {
                private long c = 0;
                private long s = 1;

                @Override
                public void write(int b) {
                    if (b == '\n') {
                        if (c == 0)
                            log.debug("Backup for server %s just started".formatted(server));
                        c++;
                    }
                }

                @Override
                public void flush() {
                    if (size == 0)
                        return;
                    final int b = 8;
                    IntStream.range(1, b)
                            .filter(n -> s == (1L << n))
                            .filter(n -> checkSize(n, b))
                            .forEach(n -> {
                                s <<= 1;
                                log.debug("Backup for server %s is %d percent done".formatted(server, 1));
                            });
                }

                @SuppressWarnings("SameParameterValue")
                private boolean checkSize(int n, int b) {
                    return (c / size) >= (n / b);
                }
            };
        }
        if (!sendSh(server.cmdBackup(), bufProgress)) {
            log.error("Backup for server %s failed".formatted(server));
            return false;
        }
        log.info("Backup for server %s finished".formatted(server));
        return true;
    }

    public boolean runUpdate() {
        if (!uploadRunScript())
            log.warn("Could not upload runscript when trying to update " + server);
        if (!uploadProperties())
            log.warn("Could not upload properties when trying to update " + server);
        if (sendSh(server.wrapCmd("./mcsd.sh update"))) {
            return true;
        }
        log.error("Could not update " + server);
        return false;
    }

    @SneakyThrows
    public boolean startServer() {
        try (var screen = attach(server.cmdStart())) {
            screen.waitForExitStatus();
            return true;
        } catch (Throwable t) {
            log.error("Could not start " + server, t);
        }
        return false;
    }

    public boolean stopServer() {
        if (!sendSh(server.wrapCmd("./mcsd.sh disable", false)))
            log.warn("Could not disable server restarts when trying to stop " + server);
        try (var screen = attach()) {
            screen.exec("stop", "^.*" + ServerConnection.EndMarker + ".*$");
            return true;
        } catch (Throwable e) {
            log.error("Could not stop " + server, e);
            return false;
        }
    }

    public AttachedConnection attach() throws JSchException {
        return attach(null);
    }

    public AttachedConnection attach(@Nullable @Language("sh") String cmd) throws JSchException {
        return new AttachedConnection(server, cmd);
    }

    public DelegateStream.Output uploadFile(final String path) throws Exception {
        synchronized (lock(':' + path)) {
            var sftp = (ChannelSftp) ssh.openChannel("sftp");
            sftp.connect();
            return new DelegateStream.Output(sftp.put(path), sftp::disconnect);
        }
    }

    public DelegateStream.Input downloadFile(final String path) throws Exception {
        synchronized (lock(':' + path)) {
            var sftp = (ChannelSftp) ssh.openChannel("sftp");
            sftp.connect();
            return new DelegateStream.Input(sftp.get(path), sftp::disconnect);
        }
    }

    public boolean sendSh(@Language("sh") String cmd) {
        return sendSh(cmd, null);
    }

    public boolean sendSh(@Language("sh") String cmd, @Nullable OutputStream stdout) {
        return sendSh(cmd, stdout, null);
    }

    public boolean sendSh(@Language("sh") String cmd, @Nullable OutputStream stdout, @Nullable OutputStream stderr) {
        return sendSh(cmd, stdout, stderr, null);
    }

    @SuppressWarnings("resource")
    public boolean sendSh(@Language("sh") String cmd, @Nullable OutputStream stdout, @Nullable OutputStream stderr, @Nullable InputStream stdin) {
        synchronized (lock('$' + cmd)) {
            ChannelExec exec = null;
            try {
                exec = (ChannelExec) ssh.openChannel("exec");
                exec.setCommand(cmd);

                var prefix = "[" + shConnection() + " $ " + cmd + "] ";
                exec.setOutputStream(Objects.requireNonNullElseGet(stdout, () -> new DelegateStream.Output(log, Level.INFO).withPrefix(prefix)));
                exec.setErrStream(Objects.requireNonNullElseGet(stderr, () -> new DelegateStream.Output(log, Level.ERROR).withPrefix(prefix)));
                if (stdin != null) exec.setInputStream(stdin, true);

                exec.connect();
                exec.start();

                while (exec.getExitStatus() == -1)
                    //noinspection BusyWait
                    Thread.sleep(10);

                return true;
            } catch (Exception e) {
                log.error("Could not send command to server " + server, e);
            } finally {
                if (exec != null)
                    exec.disconnect();
            }
            return false;
        }
    }

    public Optional<RconResponse> sendCmdRCon(final String cmd) {
        if (rcon == null)
            return Optional.empty();
        tryRcon();
        return rcon.minecraftRcon().map(rc -> {
            try {
                return rc.sendSync(() -> cmd);
            } catch (RconCommandException e) {
                log.warn("Internal error occurred when sending command %s to server %s".formatted(cmd, server), e);
                return null;
            }
        });
    }

    @Override
    public void close() {
        if (ssh != null)
            ssh.disconnect();
        if (rcon != null)
            rcon.disconnect();
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
        return locks.computeIfAbsent(ssh.getHost() + route, $ -> new Object());
    }

    public enum BackupMethod implements BitmaskAttribute<BackupMethod> {
        Evaluate,
        RCon,
        Screen
    }
}
