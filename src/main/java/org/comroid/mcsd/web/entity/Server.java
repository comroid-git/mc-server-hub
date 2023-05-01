package org.comroid.mcsd.web.entity;

import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Data;
import org.comroid.api.BitmaskAttribute;
import org.comroid.api.IntegerAttribute;
import org.comroid.mcsd.web.exception.EntityNotFoundException;
import org.comroid.mcsd.web.exception.InsufficientPermissionsException;
import org.comroid.mcsd.web.repo.ShRepo;
import org.comroid.mcsd.web.util.ApplicationContextProvider;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Data
@Entity
public class Server {
    @Id
    private UUID id = UUID.randomUUID();
    private UUID shConnection;
    private String name;
    private String mcVersion = "1.19.1";
    private int port = 25565;
    private String directory = "~/minecraft";
    private Mode mode = Mode.Paper;
    private byte ramGB = 4;
    private boolean autoStart = false;
    private int maxPlayers = 20;
    private int queryPort = 25565;
    private int rConPort = 25575;
    private String rConPassword;
    @ElementCollection
    private Map<UUID, Integer> userPermissions = new ConcurrentHashMap<>();

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

    public String cmdStart() {
        return ("cd \"%s\" || (echo \"Could change to server directory\" && return)" +
                " && (screen -dmS %s ./mcsd.sh run %dG)" +
                " && exit").formatted(getDirectory(), getUnitName(), getRamGB());
    }

    public String cmdAttach() {
        return ("cd \"%s\" || (echo \"Could change to server directory\" && return)" +
                " && (screen -DSRq %s ./mcsd.sh run %dG)" +
                " && exit").formatted(getDirectory(), getUnitName(), getRamGB());
    }

    public String cmdStop() {
        return ("rm %s/.running" +
                " && exit").formatted(getDirectory());
    }

    public String cmdBackup() {
        return ("cd \"%s\" || (echo \"Could change to server directory\" && return)" +
                " && ./mcsd.sh backup %s" +
                " && exit").formatted(getDirectory(), ApplicationContextProvider.bean(ShRepo.class)
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
