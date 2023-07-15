package org.comroid.mcsd.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Data;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@Data
@Entity
public class DiscordBotInfo {
    @Id
    private UUID id = UUID.randomUUID();
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
