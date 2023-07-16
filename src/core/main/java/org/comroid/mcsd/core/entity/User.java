package org.comroid.mcsd.core.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.comroid.api.BitmaskAttribute;
import org.comroid.mcsd.core.exception.InsufficientPermissionsException;

import java.util.UUID;

@Data
@Entity
@EqualsAndHashCode(callSuper = true)
public class User extends AbstractEntity {
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
