package org.comroid.mcsd.core.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.OneToOne;
import lombok.Getter;
import lombok.Setter;
import org.comroid.api.BitmaskAttribute;
import org.comroid.mcsd.core.model.IUser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@Getter
@Setter
@Entity
public class UserData extends AbstractEntity implements IUser {
    private @OneToOne @Nullable User user;
    private @OneToOne @Nullable MinecraftProfile minecraft;
    private @Nullable Long discordId;

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
