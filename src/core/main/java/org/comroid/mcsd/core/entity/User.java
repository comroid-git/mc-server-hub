package org.comroid.mcsd.core.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.comroid.api.BitmaskAttribute;
import org.comroid.util.REST;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@Getter
@Setter
@Entity
public class User extends AbstractEntity {
    private @Column(unique = true) @Nullable UUID hubId;
    private @Column(unique = true) @Nullable UUID minecraftId;
    private @Column(unique = true) @Nullable Long discordId;
    private @Column(unique = true)
    @ToString.Exclude
    @Getter(onMethod = @__(@JsonIgnore))
    @Nullable String verification;

    public String getMinecraftName() {
        return REST.get(getMojangAccountUrl(minecraftId))
                .thenApply(rsp -> rsp.getBody().get("name").asString())
                .join();
    }

    @Override
    public String toString() {
        return "User " + getName();
    }

    @SneakyThrows
    public String getNameMcURL() {
        return "https://namemc.com/profile/" + getMinecraftId();
    }

    @SneakyThrows
    public String getHeadURL() {
        return "https://mc-heads.net/avatar/" + getMinecraftId();
    }

    @SneakyThrows
    public String getIsoBodyURL() {
        return "https://mc-heads.net/body/" + getMinecraftId();
    }

    public static String getMojangAccountUrl(String username) {
        return "https://api.mojang.com/users/profiles/minecraft/" + username;
    }

    public static String getMojangAccountUrl(UUID id) {
        return "https://api.mojang.com/user/profile/" + id;
    }

    @Deprecated
    public enum Perm implements BitmaskAttribute<Perm> {None, ManageServers, ManageShConnections, Admin;}
}
