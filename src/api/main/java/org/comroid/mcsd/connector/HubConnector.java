package org.comroid.mcsd.connector;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.comroid.api.Container;
import org.comroid.api.IntegerAttribute;
import org.comroid.mcsd.connector.gateway.GatewayClient;
import org.comroid.mcsd.connector.gateway.GatewayPacket;
import org.comroid.util.Debug;

import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
public final class HubConnector extends Container.Base implements GatewayPacket.Creator {
    public static final String DefaultBaseUrl = "http"+(Debug.isDebug() ? "://localhost:42064" : "s://mc.comroid.org")+"/connector";
    public static final int Port = 42065;
    private final GatewayClient gateway = new GatewayClient(this);
    private final UUID uuid = UUID.randomUUID();
    private final String hubBaseUrl;
    private final Role role;

    public HubConnector(String hubBaseUrl, Role role, String token) {
        this.hubBaseUrl = hubBaseUrl;
        this.role = role;

        gateway.publish("connect", connect());
    }

    @Override
    public void closeSelf() {
        gateway.close();
    }

    public enum Role implements IntegerAttribute { Agent, Spigot, Forge, Fabric }
}
