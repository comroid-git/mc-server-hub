package org.comroid.mcsd.core.entity.server;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.github.rmmccann.minecraft.status.query.MCQuery;
import io.graversen.minecraft.rcon.Defaults;
import jakarta.persistence.*;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import me.dilley.MineStat;
import org.comroid.annotations.Ignore;
import org.comroid.api.attr.BitmaskAttribute;
import org.comroid.api.attr.IntegerAttribute;
import org.comroid.api.data.seri.DataStructure;
import org.comroid.api.func.ext.Wrap;
import org.comroid.api.func.util.Streams;
import org.comroid.api.net.Token;
import org.comroid.mcsd.api.dto.StatusMessage;
import org.comroid.mcsd.api.model.Status;
import org.comroid.mcsd.core.MCSD;
import org.comroid.mcsd.core.ServerManager;
import org.comroid.mcsd.core.entity.AbstractEntity;
import org.comroid.mcsd.core.entity.module.FileModulePrototype;
import org.comroid.mcsd.core.entity.module.ModulePrototype;
import org.comroid.mcsd.core.entity.system.Agent;
import org.comroid.mcsd.core.entity.system.DiscordBot;
import org.comroid.mcsd.core.entity.system.ShConnection;
import org.comroid.mcsd.core.model.ModuleType;
import org.comroid.mcsd.core.module.FileModule;
import org.comroid.mcsd.core.module.ServerModule;
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
import java.util.function.Predicate;
import java.util.stream.Collectors;
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
    private @ElementCollection(fetch = FetchType.EAGER) List<String> tickerMessages;
    private @Nullable @ManyToOne Agent agent; // todo: make not nullable
    private @Ignore(DataStructure.class) @Deprecated int rConPort = Defaults.RCON_PORT;
    private @Ignore(DataStructure.class) @Deprecated @Getter(onMethod = @__(@JsonIgnore)) String rConPassword = Token.random(16, false);
    private @Ignore(DataStructure.class) @Deprecated @ManyToOne ShConnection shConnection;
    private @Ignore(DataStructure.class) @Deprecated @ManyToOne @Nullable DiscordBot discordBot;
    private @Ignore(DataStructure.class) @Deprecated @Nullable String PublicChannelWebhook;
    private @Ignore(DataStructure.class) @Deprecated @Nullable @Column(unique = true) Long PublicChannelId;
    private @Ignore(DataStructure.class) @Deprecated @Nullable Long ModerationChannelId;
    private @Ignore(DataStructure.class) @Deprecated @Nullable @Column(unique = true) Long ConsoleChannelId;
    private @Ignore(DataStructure.class) @Deprecated @Nullable String ConsoleChannelPrefix;
    private @Ignore(DataStructure.class) @Deprecated long publicChannelEvents = 0xFFFF_FFFF;
    private @Ignore(DataStructure.class) @Deprecated boolean fancyConsole = true;
    private @Ignore(DataStructure.class) @Deprecated boolean forceCustomJar = false;
    private @Ignore(DataStructure.class) @Deprecated @Nullable @Column(columnDefinition = "TEXT") String customCommand = null;
    private @Ignore(DataStructure.class) @Deprecated byte ramGB = 4;
    private @Ignore(DataStructure.class) @Deprecated @Nullable Duration backupPeriod = Duration.ofHours(12);
    private @Ignore(DataStructure.class) @Deprecated Instant lastBackup = Instant.ofEpochMilli(0);
    private @Ignore(DataStructure.class) @Deprecated @Nullable Duration updatePeriod = Duration.ofDays(7);
    private @Ignore(DataStructure.class) @Deprecated Instant lastUpdate = Instant.ofEpochMilli(0);

    public Set<ModuleType<?, ?>> getFreeModuleTypes() {
        var existing = Streams.of(bean(MCSD.class)
                        .getModules()
                        .findAllByServerId(getId()))
                .map(ModulePrototype::getDtype)
                .toList();
        return ModuleType.cache.values()
                .stream()
                .filter(Predicate.not(existing::contains))
                .collect(Collectors.toUnmodifiableSet());
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

    @Language("sh")
    private String wrapDevNull(@Language("sh") String cmd) {
        return "export TERM='xterm' && script -q /dev/null < <(echo \""+cmd+"\"; cat)";
        //return "export TERM='xterm' && echo \""+cmd+"\" | script /dev/null";
    }

    @Override
    public String toString() {
        return "Server " + getName();
    }

    @Ignore(DataStructure.class)
    public String getDashboardURL() {
        return "https://mc.comroid.org/server/" + getId();
    }

    @Ignore(DataStructure.class)
    public String getViewURL() {
        return "https://mc.comroid.org/server/view/" + getId();
    }

    public String getAddress() {
        return host + ":" + port;
    }

    @Ignore(DataStructure.class)
    public String getThumbnailURL() {
        return "https://mc-api.net/v3/server/favicon/" + getAddress();
    }

    @Ignore(DataStructure.class)
    public String getStatusURL() {
        return "https://mc-api.net/v3/server/ping/" + getAddress();
    }

    @Ignore(DataStructure.class)
    public String getJarInfoUrl() {
        var type = switch(mode){
            case Vanilla -> "vanilla";
            case Paper -> "servers";
            case Forge, Fabric -> "modded";
        };
        return "https://serverjars.com/api/fetchDetails/%s/%s/%s".formatted(type,mode.name().toLowerCase(),mcVersion);
    }

    @Ignore(DataStructure.class)
    public String getJarUrl() {
        var type = switch(mode){
            case Vanilla -> "vanilla";
            case Paper -> "servers";
            case Forge, Fabric -> "modded";
        };
        return "https://serverjars.com/api/fetchJar/%s/%s/%s".formatted(type,mode.name().toLowerCase(),mcVersion);
    }

    @Ignore(DataStructure.class)
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

    public <T extends ServerModule<?>> Wrap<T> component(Class<T> type) {
        return Wrap.ofStream(components(type));
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
