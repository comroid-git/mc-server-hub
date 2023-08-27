package org.comroid.mcsd.core.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.comroid.api.BitmaskAttribute;
import org.comroid.mcsd.core.repo.UserDataRepo;
import org.comroid.mcsd.core.repo.UserRepo;
import org.comroid.mcsd.core.util.ApplicationContextProvider;
import org.jetbrains.annotations.Nullable;

@Getter
@Setter
@Entity
public class User extends AbstractEntity {
    private boolean guest;
    private @OneToOne @Nullable UserData userData;
    private @Deprecated @OneToOne @Nullable MinecraftProfile minecraft;
    private @Deprecated @Nullable Long discordId;

    public void migrate() {
        if (userData == null || userData.getUser() == null || minecraft != null || discordId != null) {
            var users = ApplicationContextProvider.bean(UserRepo.class);
            var data = ApplicationContextProvider.bean(UserDataRepo.class);
            data.save(userData = data.findByUserId(getId()).orElseGet(UserData::new)
                    .setUser(this)
                    .setMinecraft(minecraft)
                    .setDiscordId(discordId));
            minecraft = null;
            discordId = null;
            users.save(this);
        }
    }

    @Override
    public String toString() {
        return "User " + getName();
    }

    @Deprecated
    public enum Perm implements BitmaskAttribute<Perm> {None, ManageServers, ManageShConnections, Admin}
}
