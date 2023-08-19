package org.comroid.mcsd.core.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.comroid.api.BitmaskAttribute;
import org.comroid.mcsd.core.exception.InsufficientPermissionsException;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "user")
public class User extends AbstractEntity {
    private boolean guest;
    private @ManyToOne @Nullable MinecraftProfile minecraft;
    private @Nullable Long discordId;

    @Override
    public String toString() {
        return "User " + getName();
    }

    @Deprecated
    public enum Perm implements BitmaskAttribute<Perm> {None, ManageServers, ManageShConnections, Admin}
}
