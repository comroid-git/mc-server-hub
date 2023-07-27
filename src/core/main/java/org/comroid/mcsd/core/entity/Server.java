package org.comroid.mcsd.core.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.graversen.minecraft.rcon.Defaults;
import jakarta.persistence.*;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.comroid.api.BitmaskAttribute;
import org.comroid.api.IntegerAttribute;
import org.comroid.mcsd.core.exception.EntityNotFoundException;
import org.comroid.mcsd.core.exception.InsufficientPermissionsException;
import org.comroid.mcsd.core.model.ServerConnection;
import org.comroid.mcsd.core.repo.ShRepo;
import org.comroid.mcsd.core.util.ApplicationContextProvider;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Getter
@Entity
@Table(name = "server")
@RequiredArgsConstructor
public class Server extends AbstractEntity {
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
    private @Setter boolean maintenance = false;
    private @Setter int maxPlayers = 20;
    private @Setter int queryPort = 25565;
    private @Setter int rConPort = Defaults.RCON_PORT;
    private @Setter String rConPassword = UUID.randomUUID().toString();
    private @Setter Duration backupPeriod = Duration.ofHours(12);
    private @Setter Duration updatePeriod = Duration.ofDays(7);
    private @Setter Instant lastBackup = Instant.ofEpochMilli(0);
    private @Setter Instant lastUpdate = Instant.ofEpochMilli(0);
    @JsonIgnore
    @ElementCollection(fetch = FetchType.EAGER)
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

    public enum Mode implements IntegerAttribute {
        Vanilla, Paper, Forge, Fabric
    }

    public enum Permission implements BitmaskAttribute<Permission> {
        Status, Start, Stop, Console, Backup, Files
    }
}
