package org.comroid.mcsd.spigot;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class MCSD_Spigot extends JavaPlugin {
    private void initConfigDefaults(FileConfiguration config) {
        config.addDefault("mcsd.hubBaseUrl", "<mcsd hub base url>");
        config.addDefault("mcsd.agent.serverId", "<mcsd ServerAgent id>");
        config.addDefault("mcsd.agent.token", "<mcsd ServerAgent token>");

        saveConfig();
    }
}
