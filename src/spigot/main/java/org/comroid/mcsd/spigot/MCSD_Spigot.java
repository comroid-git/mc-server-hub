package org.comroid.mcsd.spigot;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class MCSD_Spigot extends JavaPlugin {
    @Override
    public void onLoad() {
        var config = getConfig();
        initConfigDefaults(config);
    }

    private void initConfigDefaults(FileConfiguration config) {
        config.addDefault("mcsd.hubBaseUrl", "<mcsd hub base url>");
        config.addDefault("mcsd.agent.id", "<mcsd agent id>");
        config.addDefault("mcsd.agent.token", "<mcsd agent token>");
        config.addDefault("mcsd.agent.serverId", "<mcsd server id>");

        saveConfig();
    }
}
