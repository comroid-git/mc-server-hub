package org.comroid.mcsd.core.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.graversen.minecraft.rcon.service.ConnectOptions;
import io.graversen.minecraft.rcon.service.IMinecraftRconService;
import io.graversen.minecraft.rcon.service.MinecraftRconService;
import io.graversen.minecraft.rcon.service.RconDetails;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.Synchronized;
import lombok.extern.java.Log;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.channel.ClientChannelEvent;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.channel.Channel;
import org.apache.sshd.sftp.client.SftpClient;
import org.apache.sshd.sftp.client.impl.DefaultSftpClientFactory;
import org.comroid.api.DelegateStream;
import org.comroid.api.ThrowingFunction;
import org.comroid.mcsd.api.dto.StatusMessage;
import org.comroid.mcsd.api.model.Status;
import org.comroid.mcsd.core.entity.ShConnection;
import org.comroid.mcsd.core.exception.EntityNotFoundException;
import org.comroid.mcsd.core.repo.DiscordBotRepo;
import org.comroid.mcsd.core.repo.ServerRepo;
import org.comroid.mcsd.core.repo.ShRepo;
import org.comroid.mcsd.core.entity.Server;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.Nullable;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

import static org.comroid.mcsd.core.util.ApplicationContextProvider.bean;

@Log
@Getter
@Deprecated
public final class ServerConnection implements Closeable, ServerHolder {
    @Language("html")
    public static final String br = "<br/>";
    public static final String OutputBeginMarker = "~~>";
    public static final String OutputEndMarker = "<~>";
    public static final String RunScript = "mcsd.sh";
    public static final String UnitFile = "unit.properties";
    private static final Map<UUID, ServerConnection> cache = new ConcurrentHashMap<>();
    private static final Map<String, Object> locks = new ConcurrentHashMap<>();
    static final Duration shTimeout = Duration.ofMinutes(2);
    private static final Resource runscript = bean(ResourceLoader.class).getResource("classpath:/"+ServerConnection.RunScript);
    private final ShConnection con;
    @JsonIgnore @Getter
    public final Server server;
    @JsonIgnore @Getter
    private final GameConnection game;
    @JsonIgnore @Getter
    private final ClientSession session;
    @JsonIgnore @Getter
    private final SftpClient sftp;
    @JsonIgnore @Getter
    private final IMinecraftRconService rcon;

    private ServerConnection(Server server) throws IOException {
        this.server = server;
        this.con = shConnection();

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
    }

    public static ServerConnection getInstance(final Server srv) {
        return cache.computeIfAbsent(srv.getId(), ThrowingFunction.rethrowing($ -> new ServerConnection(srv)));
    }

    @Synchronized("rcon")
    boolean tryRcon() {
        try {
            if (!rcon.connectBlocking(Server.statusTimeout))
                log.log(Level.WARNING, "RCon handshake timed out for " + server);
        } catch (Exception e) {
            log.log(Level.SEVERE, "Unable to connect RCon to " + server, e);
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
        if (con.server.status().join().getStatus() == Status.Offline) {
            // then start server
            if (!con.sendSh(server.cmdStart()))
                log.log(Level.WARNING, "Auto-Starting %s did not finish successfully".formatted(server));
        } else {
            log.log(Level.FINE, "%s did not need to be started".formatted(server));
        }

        // validate RCON connection works
        tryRcon();
    }

    public boolean uploadProperties() {
        final var fileName = "server.properties";
        final var path = server.getDirectory() + '/' + fileName;

        // download & update & upload properties
        try (var in = downloadFile(path)) {
            var prop = server.updateProperties(in);
            try (var out = uploadFile(path)) {
                prop.store(out, "Managed Server Properties by MCSD");
            }
        } catch (Exception e) {
            log.log(Level.SEVERE, "Error uploading managed server.properties for server " + server, e);
            return false;
        }
        log.log(Level.FINE, "Uploaded managed properties of server " + server);
        return true;
    }

    public boolean uploadRunScript() {
        var prefix = server.getDirectory() + '/';
        try {
            // upload runscript
            try (var scriptIn = runscript.getInputStream();
                 var scriptOut = uploadFile(prefix + RunScript)) {
                scriptIn.transferTo(scriptOut);
                log.log(Level.FINE, "Uploaded runscript to Server " + server);
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
                log.log(Level.FINE, "Uploaded runscript data to Server " + server);
            }

            return true;
        } catch (Exception e) {
            log.log(Level.SEVERE, "Unable to upload runscript for Server " + server, e);
            return false;
        }
    }

    private boolean doBackup() {
        if (!sendSh(server.cmdBackup())) {
            log.log(Level.SEVERE, "Backup for server %s failed".formatted(server));
            return false;
        }
        bean(ServerRepo.class).bumpLastBackup(server);
        log.info("Backup for server %s finished".formatted(server));
        return true;
    }

    public boolean runUpdate() {
        if (!uploadRunScript())
            log.log(Level.WARNING, "Could not upload runscript when trying to update " + server);
        if (!uploadProperties())
            log.log(Level.WARNING, "Could not upload properties when trying to update " + server);
        if (sendSh(server.cmdUpdate())) {
            return true;
        }
        log.log(Level.SEVERE, "Could not update " + server);
        return false;
    }

    @SneakyThrows
    public boolean startServer() {
        return sendSh(server.cmdStart());
    }

    public boolean stopServer() {
        if (!sendSh(server.cmdDisable()))
            log.log(Level.WARNING, "Could not disable server restarts when trying to stop " + server);
        try {
            game.sendCmd("stop", "^.*" + ServerConnection.OutputEndMarker + ".*$");
            return true;
        } catch (Throwable e) {
            log.log(Level.SEVERE, "Could not stop " + server, e);
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
                log.log(Level.FINE, "Could not send command '" + cmd + "' to " + server);
                log.log(Level.FINER, "Exception was", t);
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
                .findById(UUID.randomUUID())
                .orElseThrow(() -> new EntityNotFoundException(ShConnection.class, UUID.randomUUID()));
    }

    private Object lock(String route) {
        return locks.computeIfAbsent(shConnection().getHost() + route, $ -> new Object());
    }
}
