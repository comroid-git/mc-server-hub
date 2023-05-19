package org.comroid.mcsd.web.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.graversen.minecraft.rcon.Defaults;
import jakarta.persistence.*;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.comroid.api.BitmaskAttribute;
import org.comroid.api.IntegerAttribute;
import org.comroid.mcsd.web.exception.EntityNotFoundException;
import org.comroid.mcsd.web.exception.InsufficientPermissionsException;
import org.comroid.mcsd.web.model.ServerConnection;
import org.comroid.mcsd.web.repo.ShRepo;
import org.intellij.lang.annotations.Language;
import org.slf4j.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.comroid.mcsd.web.util.ApplicationContextProvider.bean;

@Data
@Slf4j
@Entity
public class Server {
    @Id
    private UUID id = UUID.randomUUID();
    private UUID owner;
    private UUID shConnection;
    private String name;
    private String mcVersion = "1.19.4";
    private int port = 25565;
    private String directory = "~/minecraft";
    private Mode mode = Mode.Paper;
    private byte ramGB = 4;
    private boolean managed = false;
    private boolean maintenance = false;
    private int maxPlayers = 20;
    private int queryPort = 25565;
    private int rConPort = Defaults.RCON_PORT;
    private String rConPassword = UUID.randomUUID().toString();
    private Duration backupPeriod = Duration.ofHours(12);
    private Duration updatePeriod = Duration.ofDays(7);
    private Instant lastBackup = Instant.ofEpochMilli(0);
    private Instant lastUpdate = Instant.ofEpochMilli(0);
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
        return wrapCmd(cmd, false);
    }

    @Language("sh")
    public String wrapCmd(@Language("sh") String cmd, boolean quiet) {
        return "(cd '" + getDirectory() + "' && " +
                //"chmod 755 "+ServerConnection.RunScript+" && " +
                (quiet ? "" : "echo '" + ServerConnection.OutputMarker + "' && ") +
                "(" + (cmd.contains(ServerConnection.RunScript) && quiet ? cmd + " -q" : cmd) + ")" +
                (quiet ? "" : " || echo 'Command finished with non-zero exit code'>&2") +
                ") && " +
                (quiet ? "" : "echo '" + ServerConnection.EndMarker + "' && ") +
                "exit";
    }

    public String cmdStart() {
        return wrapCmd("./"+ServerConnection.RunScript+" start " + getUnitName(), false);
    }

    public String cmdAttach() {
        return wrapCmd("./"+ServerConnection.RunScript+" attach " + getUnitName(), false);
    }

    public String cmdBackup() {
        return wrapCmd("./"+ServerConnection.RunScript+" backup " + bean(ShRepo.class)
                .findById(shConnection)
                .orElseThrow(() -> new EntityNotFoundException(ShConnection.class, "Server " + id))
                .getBackupsDir() + '/' + getUnitName());
    }

    @Override
    public String toString() {
        return "Server " + name;
    }

    public enum Status implements IntegerAttribute {
        Unknown, Offline, Maintenance, Online
    }

    public enum Mode implements IntegerAttribute {
        Vanilla, Paper, Forge, Fabric
    }

    public enum Permission implements BitmaskAttribute<Permission> {
        Status, Start, Stop, Console, Backup, Files
    }
}
