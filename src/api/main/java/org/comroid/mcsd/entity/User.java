package org.comroid.mcsd.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Data;
import org.comroid.api.BitmaskAttribute;
import org.comroid.mcsd.exception.InsufficientPermissionsException;

import java.util.UUID;

@Data
@Entity
public class User {
    @Id
    private UUID id = UUID.randomUUID();
    private String name;
    private boolean guest;
    private int permissions;

    public boolean canManageServers() {
        return Perm.ManageServers.isFlagSet(permissions);
    }

    public boolean canManageShConnections() {
        return Perm.ManageShConnections.isFlagSet(permissions);
    }

    public User require(Perm perm) throws InsufficientPermissionsException {
        if (!perm.isFlagSet(permissions))
            throw new InsufficientPermissionsException(this, perm);
        return this;
    }

    @Override
    public String toString() {
        return "User " + name;
    }

    public enum Perm implements BitmaskAttribute<Perm> {None, ManageServers, ManageShConnections, Admin}
}
