package org.comroid.mcsd.connector.gateway;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.comroid.mcsd.connector.HubConnector;

import java.net.InetSocketAddress;
import java.net.Socket;

@Data
@RequiredArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class GatewayClient extends GatewayActor {
    private final HubConnector connector;

    @Override
    @SneakyThrows
    public void start() {
        var socket = new Socket(HubConnector.DefaultBaseUrl, HubConnector.Port);
        socket.connect(InetSocketAddress.createUnresolved(HubConnector.DefaultBaseUrl, HubConnector.Port));
        handle(socket);
        addChildren(socket);
    }
}
