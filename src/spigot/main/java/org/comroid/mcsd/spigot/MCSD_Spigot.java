package org.comroid.mcsd.spigot;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.comroid.api.Polyfill;
import org.comroid.api.func.util.Command;
import org.comroid.api.net.REST;
import org.comroid.mcsd.api.dto.comm.PlayerEvent;
import org.comroid.util.PathUtil;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.logging.Level;

public final class MCSD_Spigot extends JavaPlugin {
    public static final String DefaultHubBaseUrl = "https://mc.comroid.org";
    public static final String DefaultRabbitUri = "amqp://anonymous:anonymous@rabbitmq.comroid.org:5672/mcsd";
    private EventManager eventManager;
    private FileConfiguration config;
    private Command.Manager cmdr;

    @Override
    public void onLoad() {
        this.config = getConfig();
        initConfigDefaults();

        this.cmdr = new Command.Manager();
        cmdr.new Adapter$Spigot(this);
        cmdr.register(this);
        cmdr.initialize();
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

    @Command(permission = "mcsd.reload")
    public String reload() {
        onDisable();
        onEnable();
        return "Reloaded!";
    }

    @Override
    public void onDisable() {
        eventManager.close();
    }

    private String hub(String path) {
        path = PathUtil.sanitize(path);
        if (!path.startsWith("/"))
            path = '/' + path;
        return config.getString("mcsd.hubBaseUrl") + "/api/open" + path;
    }

    private REST.Request req(REST.Method method, String path) {
        return REST.request(method, hub(path))
                .addHeader("Authorization", config.getString("mcsd.agent.token"));
    }

    private void initConfigAttribute(String path, @Nullable String defaultValue, String hint) {
        config.addDefault(path, Objects.requireNonNull(defaultValue, "no config value set for " + path));
        if (!config.contains(path))
            config.setComments(path, List.of(hint));
    }

    private void initConfigDefaults() {
        initConfigAttribute("mcsd.hubBaseUrl", DefaultHubBaseUrl, "MCSD Hub Base URL");
        initConfigAttribute("mcsd.rabbitMqUri", DefaultRabbitUri, "MCSD RabbitMQ URI");
        initConfigAttribute("mcsd.server.id", null, "MCSD Server ID");

        saveConfig();
    }
}
