package org.comroid.mcsd.core.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.comroid.api.BitmaskAttribute;
import org.comroid.mcsd.core.model.IUser;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@Getter
@Setter
@Entity
public class User extends AbstractEntity implements IUser {
    private @Column(unique = true) @Nullable UUID hubId;
    private @Column(unique = true) @Nullable UUID minecraftId;
    private @Column(unique = true) @Nullable Long discordId;
    private @Column(unique = true)
    @ToString.Exclude
    @Getter(onMethod = @__(@JsonIgnore))
    @Nullable String verification;

    @Override
    public String toString() {
        return "User " + getName();
    }

    @Override
    public UUID getUserId() {
        return getId();
    }

    @Deprecated
    public enum Perm implements BitmaskAttribute<Perm> {None, ManageServers, ManageShConnections, Admin}
}
