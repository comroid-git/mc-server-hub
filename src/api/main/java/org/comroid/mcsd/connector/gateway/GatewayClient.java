package org.comroid.mcsd.connector.gateway;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.java.Log;
import org.comroid.api.Event;
import org.comroid.mcsd.connector.HubConnector;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.UUID;

@Log
@Data
@RequiredArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class GatewayClient extends GatewayActor {
    private final HubConnector connector;
    private ConnectionHandler handler;

    @Override
    public GatewayConnectionData getConnectionData(UUID $) {
        return connector.getConnectionData();
    }

    @Override
    @SneakyThrows
    public void start() {
        var socket = new Socket(connector.getHubBaseUrl(), HubConnector.Port);
        socket.connect(InetSocketAddress.createUnresolved(connector.getHubBaseUrl(), HubConnector.Port));
        handler = handle(socket);
        addChildren(socket);

        next(handler.with("close", GatewayPacket.Type.Close)).thenRun(this::close);

        publish("connect", handler.connect(connector.getConnectionData()).build());
    }

    @Override
    public void closeSelf() {
        publish("close", handler.empty(GatewayPacket.Type.Close).build());
        super.closeSelf();
    }

    @Event.Subscriber
    public void handshake(GatewayPacket packet) {
        log.fine("Handshake with %s successful".formatted(packet.parse(UUID.class)));
    }

    @Event.Subscriber
    public void heartbeat(Event<GatewayPacket> event) {
        if (!event.getData().isHeartbeatValid())
            close();
        publish("heartbeat", handler.data(handler.uuid).build());
    }
}
