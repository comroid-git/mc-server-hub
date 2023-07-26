package org.comroid.mcsd.core.entity;

import jakarta.persistence.Basic;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@Data
@Entity
@Table(name = "discord_bot")
@EqualsAndHashCode(callSuper = true)
public class DiscordBotInfo extends AbstractEntity {
    @Basic
    private String token;
    private long ServerId;
    private @Nullable Long PublicChannelId;
    private @Nullable Long ModerationChannelId;
    private @Nullable Long ConsoleChannelId;
    private boolean UseDiscordWhitelist;

    @Override
    public String toString() {
        return "DiscordBotInfo";
    }
}
