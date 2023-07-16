package org.comroid.mcsd.core.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@Data
@Entity
@EqualsAndHashCode(callSuper = true)
public class DiscordBotInfo extends AbstractEntity {
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
