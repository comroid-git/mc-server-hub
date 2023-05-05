package org.comroid.mcsd.web.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.comroid.api.BitmaskAttribute;
import org.comroid.api.IntegerAttribute;
import org.comroid.mcsd.web.exception.EntityNotFoundException;
import org.comroid.mcsd.web.exception.InsufficientPermissionsException;
import org.comroid.mcsd.web.model.ServerConnection;
import org.comroid.mcsd.web.repo.ShRepo;
import org.comroid.mcsd.web.util.ApplicationContextProvider;
import org.intellij.lang.annotations.Language;

import java.time.Duration;
import java.util.*;
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
    private int rConPort = 25575;
    private String rConPassword;
    @JsonIgnore
    @ElementCollection(fetch = FetchType.EAGER)
    private Map<UUID, Integer> userPermissions = new ConcurrentHashMap<>();
    @Transient
    @JsonIgnore
    private ServerConnection connection;

    @PostLoad
    private void init() {
        connection = new ServerConnection(this);
    }

    public boolean isVanilla() {
        return mode == Mode.Vanilla;
    }

    public boolean isPaper() {
        return mode == Mode.Paper;
    }

    public boolean isForge() {
        return mode == Mode.Forge;
    }

    public boolean isFabric() {
        return mode == Mode.Fabric;
    }

    public void validateUserAccess(User user, Permission... permissions) {
        var insufficient = Arrays.stream(permissions)
                .filter(x -> !x.isFlagSet(userPermissions.getOrDefault(user.getId(), 0)))
                .toArray(Permission[]::new);
        if (insufficient.length > 0)
            throw new InsufficientPermissionsException(user, this, insufficient);
    }

    public String getUnitName() {
        return "mcsd-" + getName();
    }

    private String wrapCmd(@Language("sh") String act) {
        return ("cd \"%s\" || (echo \"Could not change to server directory\" && exit)" +
                " && (chmod 744 mcsd.sh || (echo \"Could not chmod runscript\" && exit))" +
                " && ((%s) || (echo \"Command finished with non-zero exit code\" && exit))" +
                " && exit").formatted(getDirectory(), act);
    }

    public String cmdStart() {
        return wrapCmd("./mcsd.sh start " + getUnitName() + " " + getRamGB() + "G");
    }

    public String cmdAttach() {
        return wrapCmd("./mcsd.sh attach " + getUnitName() + " " + getRamGB() + "G");
    }

    public String cmdStop() {
        return wrapCmd("rm .running");
    }

    public String cmdBackup() {
        return wrapCmd("./mcsd.sh backup " + bean(ShRepo.class)
                .findById(shConnection)
                .orElseThrow(() -> new EntityNotFoundException(ShConnection.class, "Server " + id))
                .getBackupsDir() + '/' + getUnitName());
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
