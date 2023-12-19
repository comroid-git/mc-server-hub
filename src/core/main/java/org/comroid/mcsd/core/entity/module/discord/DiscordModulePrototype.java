package org.comroid.mcsd.core.entity.module.discord;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.comroid.mcsd.core.entity.module.ModulePrototype;
import org.comroid.mcsd.core.entity.system.DiscordBot;
import org.jetbrains.annotations.Nullable;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DiscordModulePrototype extends ModulePrototype {
    private @Nullable @ManyToOne DiscordBot discordBot;
    private @Nullable String publicChannelWebhook;
    private @Nullable @Column(unique = true) Long publicChannelId;
    private @Nullable Long moderationChannelId;
    private @Nullable @Column(unique = true) Long consoleChannelId;
    private @Nullable String consoleChannelPrefix;
    private @Nullable Integer publicChannelEvents = 0xFFFF_FFFF;
    private @Nullable Boolean fancyConsole = true;
}
