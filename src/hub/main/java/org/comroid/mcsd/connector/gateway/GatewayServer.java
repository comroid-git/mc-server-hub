package org.comroid.mcsd.connector.gateway;

import jakarta.annotation.PostConstruct;
import lombok.*;
import lombok.extern.java.Log;
import org.comroid.api.Container;
import org.comroid.api.Event;
import org.comroid.api.N;
import org.comroid.api.os.OS;
import org.comroid.mcsd.connector.HubConnector;
import org.comroid.mcsd.api.dto.StatusMessage;
import org.comroid.mcsd.core.entity.Agent;
import org.comroid.mcsd.core.exception.EntityNotFoundException;
import org.comroid.mcsd.core.exception.StatusCode;
import org.comroid.mcsd.core.repo.AgentRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

@Log
@Data
@Component
@Lazy(false)
@NoArgsConstructor(force = true)
@EqualsAndHashCode(callSuper = true)
public class GatewayServer extends GatewayActor implements Runnable {
    private final Map<UUID, Connection> connections = new ConcurrentHashMap<>();
    private final ServerSocket server;
    @Autowired
    private AgentRepo agentRepo;

    @Override
    @SneakyThrows
    @PostConstruct
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
        executor.execute(this);
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
    public GatewayConnectionInfo getConnectionData(UUID handlerId) {
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
        private GatewayConnectionInfo connectionData;

        @Override
        public Stream<Object> streamOwnChildren() {
            return Stream.of(handler);
        }

        @Event.Subscriber
        public void connect(GatewayPacket packet) {
            try {
                connectionData = Objects.requireNonNull(packet).parse(GatewayConnectionInfo.class);
                var found = agentRepo.findById(connectionData.id).orElse(null);

                // validate connection
                if (found == null)
                    throw new EntityNotFoundException(Agent.class, connectionData.id);
                final var other = found.getConnectionInfo();
                final var criteria = Map.<Function<GatewayConnectionInfo,Object>,Supplier<Throwable>>of(
                        GatewayConnectionInfo::getToken, ()->new StatusCode(HttpStatus.UNAUTHORIZED, "Token mismatch"),
                        GatewayConnectionInfo::getRole, ()->new StatusCode(HttpStatus.CONFLICT, "Role mismatch"),
                        GatewayConnectionInfo::getTarget, ()->new StatusCode(HttpStatus.CONFLICT, "Target mismatch")
                );
                final Predicate<Function<GatewayConnectionInfo, Object>> check
                        = attr -> Objects.equals(attr.apply(connectionData), attr.apply(other));
                for (var entry : criteria.entrySet())
                    if (!check.test(entry.getKey()))
                        throw entry.getValue().get();

                connections.put(connectionData.id, this);
                publish("handshake", handler.data(handler.getUuid()).build());
            } catch (Throwable t) {
                t.printStackTrace();
                close();
            }
        }

        @Event.Subscriber
        public void heartbeat(GatewayPacket packet) {
            var stat = packet.parse(StatusMessage.class);

        }
    }
}
