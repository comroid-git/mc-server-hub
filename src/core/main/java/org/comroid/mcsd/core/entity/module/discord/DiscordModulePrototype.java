package org.comroid.mcsd.core.entity.module.discord;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.java.Log;
import org.comroid.mcsd.core.entity.module.ModulePrototype;
import org.comroid.mcsd.core.entity.system.DiscordBot;
import org.jetbrains.annotations.Nullable;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DiscordModulePrototype extends ModulePrototype {
    private @ManyToOne @Nullable DiscordBot discordBot;
    private @Nullable String PublicChannelWebhook;
    private @Nullable @Column(unique = true) Long PublicChannelId;
    private @Nullable Long ModerationChannelId;
    private @Nullable @Column(unique = true) Long ConsoleChannelId;
    private @Nullable String ConsoleChannelPrefix;
    private int publicChannelEvents = 0xFFFF_FFFF;
    private boolean fancyConsole = true;
}
