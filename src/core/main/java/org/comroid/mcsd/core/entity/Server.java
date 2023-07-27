package org.comroid.mcsd.core.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.github.rmmccann.minecraft.status.query.MCQuery;
import io.graversen.minecraft.rcon.Defaults;
import jakarta.persistence.*;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import me.dilley.MineStat;
import org.comroid.api.BitmaskAttribute;
import org.comroid.api.IntegerAttribute;
import org.comroid.mcsd.api.dto.StatusMessage;
import org.comroid.mcsd.api.model.Status;
import org.comroid.mcsd.core.exception.EntityNotFoundException;
import org.comroid.mcsd.core.exception.InsufficientPermissionsException;
import org.comroid.mcsd.core.model.ServerConnection;
import org.comroid.mcsd.core.repo.ShRepo;
import org.comroid.mcsd.core.util.ApplicationContextProvider;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.StreamSupport;

import static org.comroid.mcsd.core.util.ApplicationContextProvider.bean;

@Slf4j
@Getter
@Entity
@Table(name = "server")
@RequiredArgsConstructor
public class Server extends AbstractEntity {
    private static final Map<UUID, StatusMessage> statusCache = new ConcurrentHashMap<>();
    public static final Duration statusCacheLifetime = Duration.ofMinutes(1);
    public static final Duration statusTimeout = Duration.ofSeconds(10);
    private @Setter UUID owner;
    private @Setter UUID shConnection;
    private @Setter @Nullable UUID discordConnection;
    private @Setter String name;
    private @Setter String mcVersion = "1.19.4";
    private @Setter String host;
    private @Setter int port = 25565;
    private @Setter String directory = "~/minecraft";
    private @Setter Mode mode = Mode.Paper;
    private @Setter byte ramGB = 4;
    private @Setter boolean enabled = false;
    private @Setter boolean managed = false;
    private @Setter boolean maintenance = false;
    private @Setter int maxPlayers = 20;
    private @Setter int queryPort = 25565;
    private @Setter int rConPort = Defaults.RCON_PORT;
    private @Setter String rConPassword = UUID.randomUUID().toString();
    private @Setter Duration backupPeriod = Duration.ofHours(12);
    private @Setter Duration updatePeriod = Duration.ofDays(7);
    private @Setter Instant lastBackup = Instant.ofEpochMilli(0);
    private @Setter Instant lastUpdate = Instant.ofEpochMilli(0);
    @JsonIgnore @ElementCollection(fetch = FetchType.EAGER)
    private Map<UUID, Integer> userPermissions = new ConcurrentHashMap<>();

    @JsonIgnore
    public ServerConnection con() {
        return ServerConnection.getInstance(this);
    }

    @JsonIgnore
    public boolean isVanilla() {
        return mode == Mode.Vanilla;
    }

    @JsonIgnore
    public boolean isPaper() {
        return mode == Mode.Paper;
    }

    @JsonIgnore
    public boolean isForge() {
        return mode == Mode.Forge;
    }

    @JsonIgnore
    public boolean isFabric() {
        return mode == Mode.Fabric;
    }

    public Server requireUserAccess(User user, Permission... permissions) {
        var insufficient = Arrays.stream(permissions)
                .filter(x -> !x.isFlagSet(userPermissions.getOrDefault(user.getId(), 0)))
                .toArray(Permission[]::new);
        if (insufficient.length > 0)
            throw new InsufficientPermissionsException(user, this, insufficient);
        return this;
    }

    public String getUnitName() {
        return "mcsd-" + getName();
    }

    @Language("sh")
    public String wrapCmd(@Language("sh") String cmd) {
        return wrapDevNull(
                "(cd '"+getDirectory()+"' &&\n" +
                "echo '"+ServerConnection.OutputBeginMarker +"' &&\n" +
                "("+cmd+") || echo 'command failed'>&2) &&\n" +
                "echo '"+ServerConnection.OutputEndMarker +"' &&\n" +
                "exit");
        /*
        return "(cd '" + getDirectory() + "' && " +
                //"chmod 755 "+ServerConnection.RunScript+" && " +
                "echo '" + ServerConnection.OutputMarker + "' && " +
                "(" + cmd + ")" +
                " || echo 'Command finished with non-zero exit code'>&2" +
                ") && " +
                "echo '" + ServerConnection.EndMarker + "' && " +
                "exit";
         */
    }
    @Language("sh")
    private String wrapDevNull(@Language("sh") String cmd) {
        return "export TERM='xterm' && script -q /dev/null < <(echo \""+cmd+"\"; cat)";
        //return "export TERM='xterm' && echo \""+cmd+"\" | script /dev/null";
    }

    public String cmdStart() {
        return wrapCmd("./"+ServerConnection.RunScript+" start ");
    }

    public String cmdAttach() {
        return wrapCmd("./"+ServerConnection.RunScript+" attach ");
    }

    public String cmdBackup() {
        return wrapCmd("./"+ServerConnection.RunScript+" backup " + ApplicationContextProvider.bean(ShRepo.class)
                .findById(UUID.randomUUID())
                .orElseThrow(() -> new EntityNotFoundException(ShConnection.class, "Server " + getId()))
                .getBackupsDir() + '/' + getUnitName());
    }

    public String cmdUpdate() {
        return wrapCmd("./"+ServerConnection.RunScript+" update");
    }

    public String cmdDisable() {
        return wrapCmd("./"+ServerConnection.RunScript+" disable");
    }

    @Override
    public String toString() {
        return "Server " + name;
    }

    public String getDashboardURL() {
        return "https://mc.comroid.org/server/" + getId();
    }

    public String getAddress() {
        return host + ":" + port;
    }

    public String getThumbnailURL() {
        return "https://mc-api.net/v3/server/favicon/" + getAddress();
    }

    public String getStatusURL() {
        return "https://mc-api.net/v3/server/ping/" + getAddress();
    }

    public String getJarInfoUrl() {
        var type = switch(mode){
            case Vanilla -> "vanilla";
            case Paper -> "servers";
            case Forge, Fabric -> "modded";
        };
        return "https://serverjars.com/api/fetchDetails/%s/%s/%s".formatted(type,mode.name().toLowerCase(),mcVersion);
    }

    public String getJarUrl() {
        var type = switch(mode){
            case Vanilla -> "vanilla";
            case Paper -> "servers";
            case Forge, Fabric -> "modded";
        };
        return "https://serverjars.com/api/fetchJar/%s/%s/%s".formatted(type,mode.name().toLowerCase(),mcVersion);
    }

    public Path path(String... extra) {
        return Paths.get(getDirectory(), extra);
    }

    public Properties updateProperties(InputStream input) throws IOException {
        var prop = new Properties();
        prop.load(input);

        prop.setProperty("server-port", String.valueOf(getPort()));
        prop.setProperty("max-players", String.valueOf(getMaxPlayers()));
        prop.setProperty("white-list", String.valueOf(isMaintenance()));

        // query
        prop.setProperty("enable-query", String.valueOf(true));
        prop.setProperty("query.port", String.valueOf(getQueryPort()));

        // rcon
        prop.setProperty("enable-rcon", String.valueOf(getRConPassword() != null && !getRConPassword().isBlank()));
        prop.setProperty("rcon.port", String.valueOf(getRConPort()));
        prop.setProperty("rcon.password", Objects.requireNonNullElse(getRConPassword(), ""));

        return prop;
    }

    @Transient
    public StatusMessage getStatus() {
        return status().join();
    }

    @SneakyThrows
    public CompletableFuture<StatusMessage> status() {
        log.debug("Getting status of Server %s".formatted(this));
        return CompletableFuture.supplyAsync(() -> Objects.requireNonNull(statusCache.computeIfPresent(getId(), (k, v) -> {
                    if (v.getTimestamp().plus(statusCacheLifetime).isBefore(Instant.now()))
                        return null;
                    return v;
                }), "Status cache outdated"))
                .exceptionally(t -> {
                    log.debug("Unable to get server status from cache ["+t.getMessage()+"], using Query...");
                    log.trace("Exception was", t);
                    try (var query = new MCQuery(host, getQueryPort())) {
                        var stat = query.fullStat();
                        return statusCache.compute(getId(), (id, it) -> it == null ? new StatusMessage(id) : it)
                                //todo
                                //.withRcon(serverConnection.rcon.isConnected() ? Status.Online : Status.Offline)
                                //.withSsh(serverConnection.game.channel.isOpen() ? Status.Online : Status.Offline)
                                .withStatus(isMaintenance() ? Status.Maintenance : Status.Online)
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
                    var stat = new MineStat(host, getPort());
                    return statusCache.compute(getId(), (id, it) -> it == null ? new StatusMessage(id) : it)
                            //todo
                            //.withRcon(serverConnection.rcon.isConnected() ? Status.Online : Status.Offline)
                            //.withSsh(serverConnection.game.channel.isOpen() ? Status.Online : Status.Offline)
                            .withStatus(stat.isServerUp() ? isMaintenance() ? Status.Maintenance : Status.Online : Status.Offline)
                            .withPlayerCount(stat.getCurrentPlayers())
                            .withPlayerMax(stat.getMaximumPlayers())
                            .withMotd(Objects.requireNonNullElse(stat.getStrippedMotd(), "").replaceAll("§\\w", ""))
                            .withGameMode(stat.getGameMode());
                })
                .orTimeout(statusTimeout.toSeconds(), TimeUnit.SECONDS)
                .exceptionally(t -> {
                    log.warn("Unable to get server status ["+t.getMessage()+"]");
                    log.debug("Exception was", t);
                    return statusCache.compute(getId(), (id, it) -> it == null ? new StatusMessage(id) : it)
                            //todo
                            //.withRcon(serverConnection.rcon.isConnected() ? Status.Online : Status.Offline)
                            //.withSsh(serverConnection.game.channel.isOpen() ? Status.Online : Status.Offline);
                    ;
                })
                .thenApply(msg -> {
                    statusCache.put(getId(), msg);
                    return msg;
                })
                .orTimeout(statusTimeout.toSeconds() + 1, TimeUnit.SECONDS);
    }

    public Optional<ShConnection> shCon() {
        return bean(ShRepo.class).findById(shConnection);
    }

    public enum Mode implements IntegerAttribute {
        Vanilla, Paper, Forge, Fabric
    }

    public enum Permission implements BitmaskAttribute<Permission> {
        Status, Start, Stop, Console, Backup, Files
    }
}
