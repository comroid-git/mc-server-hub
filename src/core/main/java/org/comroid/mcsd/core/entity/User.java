package org.comroid.mcsd.core.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.comroid.api.BitmaskAttribute;
import org.comroid.mcsd.core.model.IUser;
import org.comroid.mcsd.core.repo.UserDataRepo;
import org.comroid.mcsd.core.repo.UserRepo;
import org.comroid.mcsd.core.util.ApplicationContextProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@Getter
@Setter
@Entity
public class User extends AbstractEntity implements IUser {
    private boolean guest;
    private @OneToOne @NotNull @Getter(onMethod = @__(@JsonIgnore)) UserData userData = new UserData();

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
