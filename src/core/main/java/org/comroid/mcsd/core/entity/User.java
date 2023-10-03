package org.comroid.mcsd.core.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import lombok.extern.java.Log;
import org.comroid.api.BitmaskAttribute;
import org.comroid.api.Rewrapper;
import org.comroid.mcsd.api.dto.McsdConfig;
import org.comroid.mcsd.core.module.discord.DiscordAdapter;
import org.comroid.mcsd.core.util.ApplicationContextProvider;
import org.comroid.util.Constraint;
import org.comroid.util.REST;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.logging.Level;

import static org.comroid.api.Rewrapper.empty;
import static org.comroid.api.Rewrapper.of;
import static org.comroid.mcsd.core.util.ApplicationContextProvider.bean;

@Log
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

    public CompletableFuture<String> getMinecraftName() {
        Constraint.notNull(minecraftId, this+".minecraftId").run();
        return REST.get(getMojangAccountUrl(minecraftId))
                .thenApply(REST.Response::validate2xxOK)
                .thenApply(rsp -> rsp.getBody().get("name").asString())
                .exceptionally(t-> {
                    log.log(Level.WARNING, "Could not retrieve Minecraft Username for user " + minecraftId, t);
                    return "Steve";
                });
    }

    public CompletableFuture<DisplayUser> getDiscordDisplayUser() {
        Constraint.notNull(discordId, this+".discordId").run();
        assert discordId != null;
        return bean(DiscordAdapter.class).getJda()
                .retrieveUserById(discordId)
                .map(usr->new DisplayUser(DisplayUser.Type.Discord,usr.getEffectiveName(),usr.getEffectiveAvatarUrl(),null))
                .submit();
        /*
        return REST.request(REST.Method.GET, getDiscordUserUrl(discordId))
                .addHeader("Authorization", "Bot "+bean(McsdConfig.class).getDiscordToken())
                .execute()
                .thenApply(REST.Response::validate2xxOK)
                .thenApply(rsp -> {
                    rsp.require(200, "Invalid response");
                    return rsp.getBody();
                })
                .thenApply(data->new DisplayUser(DisplayUser.Type.Discord,
                        data.get("username").asString(),
                        getDiscordAvatarUrl(discordId, data.get("avatar").asString()),
                        null))
                .exceptionally(t-> {
                    log.log(Level.WARNING, "Could not retrieve Discord User data for user " + discordId, t);
                    return new DisplayUser(DisplayUser.Type.Discord,
                            getName(),
                            "https://cdn.discordapp.com/embed/avatars/0.png",
                            null);
                });
         */
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
        Constraint.Length.min(1, types, "types").run();
        Rewrapper<DisplayUser> result = null;
        DisplayUser.Type type = DisplayUser.Type.Hub;
        int i = -1;
        do {
            try {
                do {
                    if (i + 1 >= types.length)
                        result = empty();
                    type = types[++i];
                } while (!type.test(this));
                result = type == DisplayUser.Type.Discord
                        ? of(getDiscordDisplayUser().join())
                        : of(new DisplayUser(type,
                        type == DisplayUser.Type.Minecraft
                                ? getMinecraftName().join()
                                : getName(),
                        getHeadURL(),
                        getNameMcURL()));
            } catch (Throwable t) {
                log.log(Level.WARNING, "Could not retrieve " + type + " display for for user " + getId(), t);
            }
        } while (result == null || result.isNull());
        return result;
    }

    public static String getMojangAccountUrl(String username) {
        return "https://api.mojang.com/users/profiles/minecraft/" + username;
    }

    public static String getMojangAccountUrl(UUID id) {
        return "https://api.mojang.com/user/profile/" + id;
    }

    public static String getDiscordUserUrl(long id) {
        return "https://discord.com/api/users/"+id;
    }

    public static String getDiscordAvatarUrl(long id, String hash) {
        return "https://cdn.discordapp.com/avatars/"+id+"/"+hash+".png";
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
