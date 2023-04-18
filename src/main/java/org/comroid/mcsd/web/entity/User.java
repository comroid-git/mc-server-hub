package org.comroid.mcsd.web.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.comroid.api.BitmaskAttribute;
import org.comroid.mcsd.web.exception.InsufficientPermissionsException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Entity
public class User {
    @Id
    private UUID id = UUID.randomUUID();
    private String name;
    private boolean guest;
    private int permissions;
    @ElementCollection
    private List<UUID> permittedServers = new ArrayList<>();

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

    public enum Perm implements BitmaskAttribute<Perm> { None, ManageServers, ManageShConnections }
}
