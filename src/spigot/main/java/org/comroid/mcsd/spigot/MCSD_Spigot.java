package org.comroid.mcsd.spigot;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class MCSD_Spigot extends JavaPlugin {
    private void initConfigDefaults(FileConfiguration config) {
        config.addDefault("mcsd.agent.serverId", "<mcsd server id>");
        config.addDefault("mcsd.agent.token", "<mcsd server token>");
        config.addDefault("mcsd.agent.hubBaseUrl", "<mcsd hub base url>");

        saveConfig();
    }
}
