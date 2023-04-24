package org.comroid.mcsd.web.entity;

import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Data;
import org.comroid.api.BitmaskAttribute;
import org.comroid.api.IntegerAttribute;
import org.comroid.mcsd.web.exception.InsufficientPermissionsException;

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
    private String directory = "~/minecraft/";
    private Mode mode = Mode.Paper;
    private byte ramGB = 2;
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

    public enum Status implements IntegerAttribute {
        Unknown, Offline, Maintenance, Online
    }

    public enum Mode implements IntegerAttribute {
        Vanilla, Paper, Forge, Fabric
    }

    public enum Permission implements BitmaskAttribute<Permission> {
        Status, Start, Stop, Console, Files
    }
}
