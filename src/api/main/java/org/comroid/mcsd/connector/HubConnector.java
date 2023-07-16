package org.comroid.mcsd.connector;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldDefaults;
import org.comroid.api.Container;
import org.comroid.api.IntegerAttribute;
import org.comroid.mcsd.connector.gateway.GatewayClient;
import org.comroid.mcsd.connector.gateway.GatewayConnectionData;
import org.comroid.mcsd.connector.gateway.GatewayPacket;

@Data
@EqualsAndHashCode(callSuper = true)
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public final class HubConnector extends Container.Base {
    public static final int Port = 42065;
    GatewayClient gateway;
    GatewayConnectionData connectionData;
    String hubBaseUrl;

    @lombok.Builder
    public HubConnector(String hubBaseUrl, GatewayConnectionData connectionData) {
        this.hubBaseUrl = hubBaseUrl;
        this.connectionData = connectionData;
        this.gateway = new GatewayClient(this);

        gateway.start();
    }

    @Override
    public void closeSelf() {
        gateway.close();
    }

    public enum Role implements IntegerAttribute { Agent, Server }
}
