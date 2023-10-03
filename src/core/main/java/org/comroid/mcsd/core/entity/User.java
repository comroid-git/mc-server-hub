package org.comroid.mcsd.core.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.comroid.api.BitmaskAttribute;
import org.comroid.api.Rewrapper;
import org.comroid.util.Constraint;
import org.comroid.util.REST;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Range;

import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;

@Getter
@Setter
@Entity
public class User extends AbstractEntity {
    private @Column(unique = true) @Nullable UUID hubId;
    private @Column(unique = true) @Nullable UUID minecraftId;
    private @Column(unique = true) @Nullable Long discordId;
    private @Column(unique = true) @Nullable String email;
    private @Column(unique = true)
    @ToString.Exclude
    @Getter(onMethod = @__(@JsonIgnore))
    @Nullable String verification;
    private @Column(unique = true)
    @ToString.Exclude
    @Getter(onMethod = @__(@JsonIgnore))
    @Nullable Instant verificationTimeout;

    public String getMinecraftName() {
        return REST.get(getMojangAccountUrl(minecraftId))
                .thenApply(rsp -> rsp.getBody().get("name").asString())
                .join();
    }

    @Override
    public String toString() {
        return "User " + getName();
    }

    public String getNameMcURL() {return minecraftId==null
            ?"https://github.com/comroid-git/mc-server-hub/blob/main/docs/account_not_linked.md"
            :"https://namemc.com/profile/" + minecraftId;}
    public String getHeadURL() {return minecraftId==null
            ?"https://github.com/comroid-git/mc-server-hub/blob/main/docs/account_not_linked.md"
            :"https://mc-heads.net/avatar/" + minecraftId;}
    public String getIsoBodyURL() {return minecraftId==null
            ?"https://github.com/comroid-git/mc-server-hub/blob/main/docs/account_not_linked.md"
            :"https://mc-heads.net/body/" + minecraftId;}

    public Rewrapper<DisplayUser> getDisplayUser(DisplayUser.Type... types) {
        Constraint.Length.min(1, types, "types");
        DisplayUser.Type type;
        int i = -1;
        do {
            if (i+1>=types.length)
                return Rewrapper.empty();
            type = types[++i];
        } while (!type.test(this));
        return Rewrapper.of(new DisplayUser(type,
                type == DisplayUser.Type.Minecraft ? getMinecraftName() : getName(),
                getHeadURL(),
                getNameMcURL()));
    }

    public static String getMojangAccountUrl(String username) {
        return "https://api.mojang.com/users/profiles/minecraft/" + username;
    }

    public static String getMojangAccountUrl(UUID id) {
        return "https://api.mojang.com/user/profile/" + id;
    }

    public record DisplayUser(Type type, String username, String avatarUrl, @Nullable String url) {
        public enum Type implements Predicate<User> {
            Minecraft {
                @Override
                public boolean test(User user) {
                    return user.minecraftId != null;
                }
            },
            Discord {
                @Override
                public boolean test(User user) {
                    return user.discordId != null;
                }
            },
            Hub {
                @Override
                public boolean test(User user) {
                    return user.hubId != null;
                }
            };

            @Override
            public abstract boolean test(User user);
        }
    }

    @Deprecated
    public enum Perm implements BitmaskAttribute<Perm> {None, ManageServers, ManageShConnections, Admin;}
}
