package org.comroid.mcsd.core.entity.server;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.github.rmmccann.minecraft.status.query.MCQuery;
import io.graversen.minecraft.rcon.Defaults;
import jakarta.persistence.*;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import me.dilley.MineStat;
import org.comroid.api.*;
import org.comroid.mcsd.api.dto.StatusMessage;
import org.comroid.mcsd.api.model.Status;
import org.comroid.mcsd.core.ServerManager;
import org.comroid.mcsd.core.entity.AbstractEntity;
import org.comroid.mcsd.core.entity.module.FileModulePrototype;
import org.comroid.mcsd.core.entity.system.DiscordBot;
import org.comroid.mcsd.core.entity.system.ShConnection;
import org.comroid.mcsd.core.module.FileModule;
import org.comroid.mcsd.core.module.ServerModule;
import org.comroid.util.Token;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.comroid.mcsd.core.util.ApplicationContextProvider.bean;

@Slf4j
@Getter
@Setter
@Entity
@AllArgsConstructor
@RequiredArgsConstructor
public class Server extends AbstractEntity {
    private static final Map<UUID, StatusMessage> statusCache = new ConcurrentHashMap<>();
    public static final Duration statusCacheLifetime = Duration.ofMinutes(1);
    public static final Duration statusTimeout = Duration.ofSeconds(10);
    private static final Duration TickRate = Duration.ofMinutes(1);
    private @Nullable String homepage;
    private String mcVersion = "1.19.4";
    private String host;
    private int port = 25565;
    private @Deprecated String directory = "~/minecraft";
    private Mode mode = Mode.Paper;
    private boolean enabled = false;
    private boolean managed = false;
    private boolean whitelist = false;
    private boolean maintenance = false;
    private int maxPlayers = 20;
    private int queryPort = 25565;
    private int rConPort = Defaults.RCON_PORT;
    private @Getter(onMethod = @__(@JsonIgnore)) String rConPassword = Token.random(16, false);
    private @ElementCollection(fetch = FetchType.EAGER) List<String> tickerMessages;
    private @Deprecated @ManyToOne ShConnection shConnection;
    private @Deprecated @ManyToOne @Nullable DiscordBot discordBot;
    private @Deprecated @Nullable String PublicChannelWebhook;
    private @Deprecated @Nullable @Column(unique = true) Long PublicChannelId;
    private @Deprecated @Nullable Long ModerationChannelId;
    private @Deprecated @Nullable @Column(unique = true) Long ConsoleChannelId;
    private @Deprecated @Nullable String ConsoleChannelPrefix;
    private @Deprecated int publicChannelEvents = 0xFFFF_FFFF;
    private @Deprecated boolean fancyConsole = true;
    private @Deprecated boolean forceCustomJar = false;
    private @Deprecated @Nullable @Column(columnDefinition = "TEXT") String customCommand = null;
    private @Deprecated byte ramGB = 4;
    private @Deprecated @Nullable Duration backupPeriod = Duration.ofHours(12);
    private @Deprecated Instant lastBackup = Instant.ofEpochMilli(0);
    private @Deprecated @Nullable Duration updatePeriod = Duration.ofDays(7);
    private @Deprecated Instant lastUpdate = Instant.ofEpochMilli(0);

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

    @Language("sh")
    private String wrapDevNull(@Language("sh") String cmd) {
        return "export TERM='xterm' && script -q /dev/null < <(echo \""+cmd+"\"; cat)";
        //return "export TERM='xterm' && echo \""+cmd+"\" | script /dev/null";
    }

    @Override
    public String toString() {
        return "Server " + getName();
    }

    public String getDashboardURL() {
        return "https://mc.comroid.org/server/" + getId();
    }

    public String getViewURL() {
        return "https://mc.comroid.org/server/view/" + getId();
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

    public String getLoaderName() {
        return mode.getName();
    }

    public Path path(String... extra) {
        return Paths.get(((FileModulePrototype) component(FileModule.class).assertion().getProto()).getDirectory(), extra);
    }

    @SneakyThrows
    public CompletableFuture<StatusMessage> status() {
        log.trace("Getting status of Server %s".formatted(this));
        return CompletableFuture.supplyAsync(() -> Objects.requireNonNull(statusCache.computeIfPresent(getId(), (k, v) -> {
                    if (v.getTimestamp().plus(statusCacheLifetime).isBefore(Instant.now()))
                        return null;
                    return v;
                }), "Status cache outdated"))
                .exceptionally(t ->
                {
                    log.trace("Unable to get server status from cache ["+t.getMessage()+"], using Query...");
                    log.trace("Exception was", t);
                    try (var query = new MCQuery(host, getQueryPort())) {
                        var stat = query.fullStat();
                        return statusCache.compute(getId(), (id, it) -> it == null ? new StatusMessage(id) : it)
                                //todo
                                //.withRcon(serverConnection.rcon.isConnected() ? Status.Online : Status.Offline)
                                //.withSsh(serverConnection.game.channel.isOpen() ? Status.Online : Status.Offline)
                                .withStatus(isMaintenance() ? Status.in_maintenance_mode : Status.online)
                                .withPlayerCount(stat.getOnlinePlayers())
                                .withPlayerMax(stat.getMaxPlayers())
                                .withMotd(stat.getMOTD())
                                .withGameMode(stat.getGameMode())
                                .withPlayers(stat.getPlayerList())
                                .withWorldName(stat.getMapName());
                    }
                })
                .exceptionally(t -> {
                    log.trace("Unable to get server status using Query ["+t.getMessage()+"], using MineStat...");
                    log.trace("Exception was", t);
                    var stat = new MineStat(host, getPort());
                    return statusCache.compute(getId(), (id, it) -> it == null ? new StatusMessage(id) : it)
                            //todo
                            //.withRcon(serverConnection.rcon.isConnected() ? Status.Online : Status.Offline)
                            //.withSsh(serverConnection.game.channel.isOpen() ? Status.Online : Status.Offline)
                            .withStatus(stat.isServerUp() ? isMaintenance() ? Status.in_maintenance_mode : Status.online : Status.offline)
                            .withPlayerCount(stat.getCurrentPlayers())
                            .withPlayerMax(stat.getMaximumPlayers())
                            .withMotd(Objects.requireNonNullElse(stat.getStrippedMotd(), ""))
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
                .completeOnTimeout(new StatusMessage(getId()), (long) (statusTimeout.toSeconds() * 1.5), TimeUnit.SECONDS);
    }

    public <T extends ServerModule<?>> SupplierX<T> component(Class<T> type) {
        return SupplierX.ofStream(components(type));
    }
    public <T extends ServerModule<?>> Stream<T> components(Class<T> type) {
        return bean(ServerManager.class).get(getId())
                .assertion(this+" not initialized")
                .components(type);
    }

    public enum Mode implements IntegerAttribute {
        Vanilla, Paper, Forge, Fabric
    }

    @Deprecated
    public enum Permission implements BitmaskAttribute<Permission> {
        Status, Start, Stop, Console, Backup, Files
    }
    public enum ConsoleMode implements IntegerAttribute { Append, Scroll, ScrollClean }
}
