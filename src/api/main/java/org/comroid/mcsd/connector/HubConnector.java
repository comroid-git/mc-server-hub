package org.comroid.mcsd.connector;

import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldDefaults;
import org.comroid.api.Container;
import org.comroid.api.IntegerAttribute;
import org.comroid.mcsd.connector.gateway.GatewayClient;
import org.comroid.mcsd.connector.gateway.GatewayConnectionInfo;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

@Data
@EqualsAndHashCode(callSuper = true)
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public final class HubConnector extends Container.Base {
    public static final String DefaultBaseUrl = "https://mc.comroid.org";
    public static final int Port = 42065;

    private final GatewayConnectionInfo connectionData;
    private final ScheduledExecutorService executor;
    private final GatewayClient gateway;

    @lombok.Builder
    public HubConnector(GatewayConnectionInfo connectionData, ScheduledExecutorService executor) {
        this.connectionData = connectionData;
        this.executor = executor;
        this.gateway = new GatewayClient(this);

        gateway.start();
    }

    @Override
    public void closeSelf() {
        gateway.close();
    }

    public enum Role implements IntegerAttribute { Agent, Server }
}
