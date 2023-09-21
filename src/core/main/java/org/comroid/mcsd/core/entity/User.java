package org.comroid.mcsd.core.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.*;
import lombok.*;
import org.comroid.api.BitmaskAttribute;
import org.jetbrains.annotations.Nullable;
import org.springframework.web.client.RestTemplate;

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
        return new RestTemplate().getForObject(getMojangAccountUrl(minecraftId), ObjectNode.class)
                .get("name")
                .asText();
    }

    @Override
    public String toString() {
        return "User " + getName();
    }

    @SneakyThrows
    public String getNameMcURL() {
        return "https://namemc.com/profile/" + getId();
    }

    @SneakyThrows
    public String getHeadURL() {
        return "https://mc-heads.net/avatar/" + getId();
    }

    @SneakyThrows
    public String getIsoBodyURL() {
        return "https://mc-heads.net/body/" + getId();
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
