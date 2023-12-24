package org.comroid.mcsd.spigot;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.comroid.api.Polyfill;
import org.comroid.mcsd.api.dto.PlayerEvent;
import org.comroid.util.PathUtil;
import org.comroid.util.REST;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

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
        try {
            req(REST.Method.GET, "/open/agent/hello/" + config.getString("mcsd.agent.id"))
                    .execute()
                    .thenApply(REST.Response::validate2xxOK)
                    .join();
        } catch (Throwable t) {
            getLogger().warning("Could not reach Hub @ " + config.getString("mcsd.hubBaseUrl"));
        }

        this.eventManager = new EventManager(this);

        getServer().getPluginManager().registerEvents(eventManager, this);
    }

    public String hub(String path) {
        path = PathUtil.sanitize(path);
        if (path.startsWith("/"))
            path = '/' + path;
        return config.getString("mcsd.hubBaseUrl") + path;
    }

    public REST.Request req(REST.Method method, String path) {
        return REST.request(method, hub(path))
                .addHeader("Authorization", config.getString("mcsd.agent.token"));
    }

    public void forward(PlayerEvent event) {
        req(REST.Method.POST, "/api/open/agent/%s/server/%s/player/event".formatted(
                config.getString("mcsd.agent.id"),
                config.getString("mcsd.agent.serverId")))
                .withBody(event.json())
                .execute()
                .thenApply(REST.Response::validate2xxOK)
                .exceptionally(Polyfill.exceptionLogger(getLogger()));
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
