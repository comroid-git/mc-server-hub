package org.comroid.mcsd.spigot;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.comroid.api.DelegateStream;
import org.comroid.api.Event;
import org.comroid.api.IntegerAttribute;
import org.comroid.mcsd.connector.HubConnector;
import org.comroid.mcsd.connector.gateway.GatewayClient;
import org.comroid.mcsd.connector.gateway.GatewayConnectionData;
import org.comroid.mcsd.connector.gateway.GatewayPacket;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.UUID;

import static org.comroid.api.IntegerAttribute.valueOf;

public final class MCSD_Spigot extends JavaPlugin {
    private HubConnector connector;
    private GatewayClient gateway;

    @Override
    public void onEnable() {
        var config = getConfig();
        var conInfo = config.getConfigurationSection("connectionData");
        assert conInfo != null;
        var connectionData = new GatewayConnectionData(
                valueOf(conInfo.getInt("role"), HubConnector.Role.class).assertion(),
                UUID.fromString(Objects.requireNonNull(conInfo.getString("uuid"))),
                conInfo.getString("token"));
        this.connector = new HubConnector("https://mc.comroid.org", connectionData);
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
}
