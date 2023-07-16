package org.comroid.mcsd.connector.gateway;

import lombok.*;
import org.comroid.api.Container;
import org.comroid.api.Event;
import org.comroid.api.os.OS;
import org.comroid.mcsd.connector.HubConnector;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

@Data
@Component
@NoArgsConstructor(force = true)
@EqualsAndHashCode(callSuper = true)
public class GatewayServer extends GatewayActor implements Runnable {
    private final Map<UUID, Connection> connections = new ConcurrentHashMap<>();
    private final ServerSocket server;

    @Override
    @SneakyThrows
    public void start() {
        var address = OS.isWindows ? null : InetAddress.getLoopbackAddress();
        if (address == null)
            try {
                address = InetAddress.getLocalHost();
            } catch (UnknownHostException e) {
                address = InetAddress.getLoopbackAddress();
            }

        var server = new ServerSocket(HubConnector.Port);
        server.bind(new InetSocketAddress(address, HubConnector.Port));
    }

    @Override
    @SneakyThrows
    public void run() {
        while (server.isBound()) {
            var handler = handle(server.accept());
            var connection = new Connection(handler);
            handler.addChildren(connection);
            register(connection);
        }
    }

    @Override
    public GatewayConnectionData getConnectionData(UUID handlerId) {
        return connections.values().stream()
                .filter(con -> Objects.equals(con.handler.getUuid(), handlerId))
                .findAny()
                .map(Connection::getConnectionData)
                .orElseThrow();
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    private class Connection extends Container.Base {
        private final ConnectionHandler handler;
        private GatewayConnectionData connectionData;

        @Override
        public Stream<Object> streamOwnChildren() {
            return Stream.of(handler);
        }

        @Event.Subscriber
        public void connect(Event<GatewayPacket> event) {
            connectionData = Objects.requireNonNull(event.getData()).parse(GatewayConnectionData.class);
            connections.put(connectionData.id, this);

            publish("handshake", handler.data(handler.getUuid()).build());
        }
    }
}
