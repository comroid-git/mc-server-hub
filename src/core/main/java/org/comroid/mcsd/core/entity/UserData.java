package org.comroid.mcsd.core.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToOne;
import lombok.Getter;
import lombok.Setter;
import net.dv8tion.jda.api.EmbedBuilder;
import org.comroid.api.BitmaskAttribute;
import org.comroid.mcsd.core.model.IUser;
import org.comroid.mcsd.core.module.discord.DiscordAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

@Getter
@Setter
@Entity
@Deprecated
public class UserData extends AbstractEntity implements IUser {
    private @OneToOne @Nullable User user;
    private @OneToOne @Nullable MinecraftProfile minecraft;
    private @Column(unique = true) @Nullable Long discordId;

    @Override
    public String toString() {
        return "User " + getName();
    }

    @Override
    public UUID getUserId() {
        return getId();
    }

    public EmbedBuilder toUserEmbed() {
        final var embed = new EmbedBuilder();
            embed.setAuthor(
                    Optional.<AbstractEntity>ofNullable(minecraft)
                            .or(()->Optional.ofNullable(user))
                            .map(AbstractEntity::getName)
                            .orElse("Unnamed User"),
                    Optional.ofNullable(minecraft)
                            .map(MinecraftProfile::getNameMcURL)
                            .orElseGet(this::getProfileUrl),
                    Optional.ofNullable(minecraft)
                            .map(MinecraftProfile::getHeadURL)
                            .orElse("https://cdn.discordapp.com/embed/avatars/0.png"));
        return embed;
    }

    private String getProfileUrl() {
        // todo: automatic base url
        return "https://mc.comroid.org/user/" + getId();
    }

    @Deprecated
    public enum Perm implements BitmaskAttribute<Perm> {None, ManageServers, ManageShConnections, Admin}
}
