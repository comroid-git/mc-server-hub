package org.comroid.mcsd.spigot;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.comroid.api.DelegateStream;
import org.comroid.api.Event;
import org.comroid.mcsd.connector.HubConnector;
import org.comroid.mcsd.connector.gateway.GatewayClient;
import org.comroid.mcsd.connector.gateway.GatewayConnectionInfo;
import org.comroid.mcsd.connector.gateway.GatewayPacket;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.comroid.api.IntegerAttribute.valueOf;

public final class MCSD_Spigot extends JavaPlugin {
    private HubConnector connector;
    private GatewayClient gateway;

    @Override
    public void onEnable() {
        // load config
        var config = getConfig();
        initConfigDefaults(config);

        // enable console handler
        var consoleHandler = new ConsoleHandler(){
            @Override
            public void publish(LogRecord record) {
                var eventKey = DelegateStream.IO.EventKey_Output;
                if (record.getLevel().intValue() > Level.INFO.intValue())
                    eventKey = DelegateStream.IO.EventKey_Error;
                var msg = getFormatter().format(record);
                gateway.publish(eventKey, gateway.getHandler().data(msg).build());
                super.publish(record);
            }
        };
        Logger.getGlobal().addHandler(consoleHandler);

        // connect to hub
        var connectionData = GatewayConnectionInfo.builder()
                .role(HubConnector.Role.Server)
                .agent(UUID.fromString(Objects.requireNonNull(config.getString("mcsd.agent.id"))))
                .target(UUID.fromString(Objects.requireNonNull(config.getString("mcsd.agent.serverId"))))
                .token(Objects.requireNonNull(config.getString("mcsd.agent.token")))
                .hubBaseUrl(config.getString("mcsd.agent.hubBaseUrl"))
                .build();
        assert connectionData != null;
        connectionData.setRole(HubConnector.Role.Server);
        this.connector = new HubConnector(connectionData, Executors.newSingleThreadScheduledExecutor());
        this.gateway = connector.getGateway();
        gateway.register(this);
    }

    @Event.Subscriber
    public void command(GatewayPacket packet) {
        var command = packet.parse(String.class);
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
    }

    @Override
    public void onDisable() {
        connector.close();
    }

    private void initConfigDefaults(FileConfiguration config) {
        config.addDefault("mcsd.agent.serverId", "<mcsd server id>");
        config.addDefault("mcsd.agent.token", "<mcsd server token>");
        config.addDefault("mcsd.agent.hubBaseUrl", "<mcsd hub base url>");

        saveConfig();
    }
}
