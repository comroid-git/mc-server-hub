package org.comroid.mcsd.spigot;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.comroid.api.Polyfill;
import org.comroid.api.net.REST;
import org.comroid.mcsd.api.dto.PlayerEvent;
import org.comroid.util.PathUtil;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.logging.Level;

public final class MCSD_Spigot extends JavaPlugin {
    private EventManager eventManager;
    private FileConfiguration config;

    @Override
    public void onLoad() {
        this.config = getConfig();
        initConfigDefaults();
    }

    @Override
    public void onEnable() {
        req(REST.Method.GET, "/agent/hello/" + config.getString("mcsd.agent.id"))
                .execute()
                .thenApply(REST.Response::validate2xxOK)
                .thenRun(() -> getLogger().info("Ping to Hub succeeded"))
                .exceptionally(Polyfill.exceptionLogger(getLogger(), Level.WARNING, Level.FINE, "Could not reach Hub @ " + config.getString("mcsd.hubBaseUrl")));

        this.eventManager = new EventManager(this);

        getServer().getPluginManager().registerEvents(eventManager, this);
    }

    public String hub(String path) {
        path = PathUtil.sanitize(path);
        if (!path.startsWith("/"))
            path = '/' + path;
        return config.getString("mcsd.hubBaseUrl") + "/api/open" + path;
    }

    public REST.Request req(REST.Method method, String path) {
        return REST.request(method, hub(path))
                .addHeader("Authorization", config.getString("mcsd.agent.token"));
    }

    public void forward(PlayerEvent event) {
        req(REST.Method.POST, "/agent/%s/server/%s/player/event".formatted(
                config.getString("mcsd.agent.id"),
                config.getString("mcsd.agent.serverId")))
                .setBody(event.json())
                .execute()
                .thenApply(REST.Response::validate2xxOK)
                .exceptionally(Polyfill.exceptionLogger(getLogger(), Level.WARNING, Level.FINE, "Could not forward PlayerEvent to Hub"));
    }

    @Override
    public void onDisable() {
        eventManager.close();
    }

    private void initConfigAttribute(String path, @Nullable String defaultValue, String hint) {
        config.addDefault(path, Objects.requireNonNullElse(defaultValue, "<please specify>"));
        if (!config.contains(path))
            config.setComments(path, List.of(hint));
    }

    private void initConfigDefaults() {
        initConfigAttribute("mcsd.hubBaseUrl", "https://mc.comroid.org", "MCSD Hub Base URL");
        initConfigAttribute("mcsd.agent.id", null, "MCSD Agent ID");
        initConfigAttribute("mcsd.agent.token", null, "MCSD Agent Token");
        initConfigAttribute("mcsd.agent.serverId", null, "MCSD Server ID");

        saveConfig();
    }
}
