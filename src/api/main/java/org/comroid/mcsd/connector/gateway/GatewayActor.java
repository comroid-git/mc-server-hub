package org.comroid.mcsd.connector.gateway;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.SneakyThrows;
import lombok.Value;
import org.comroid.api.*;
import org.jetbrains.annotations.Nullable;

import java.net.Socket;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Predicate;

import static org.comroid.mcsd.connector.gateway.GatewayPacket.serialize;

@Data
@EqualsAndHashCode(callSuper = true)
public abstract class GatewayActor extends Event.Bus<GatewayPacket> implements Startable {
    protected final ExecutorService executor = Executors.newFixedThreadPool(8);

    protected abstract GatewayConnectionData getConnectionData(UUID handlerId);

    protected ConnectionHandler handle(Socket socket) {
        var handler = new ConnectionHandler(socket);
        register(handler);
        return handler;
    }

    @Value
    @EqualsAndHashCode(callSuper = true)
    protected class ConnectionHandler extends Container.Base {
        UUID uuid = UUID.randomUUID();

        @SneakyThrows
        public ConnectionHandler(Socket socket) {
            final var io = new DelegateStream.IO(socket.getInputStream(), socket.getOutputStream(), null)
                    .useCompression()
                    .useEncryption(getConnectionData(uuid)::toCipher);
            final var output = io.toPrintStream();
            addChildren(
                    // Rx
                    new DelegateStream.Input(socket.getInputStream())
                            .setEndlMode(DelegateStream.EndlMode.OnNewLine)
                            .subscribe(str -> {
                                var packet = parsePacket(str).setReceived(true);
                                publish(packet.getTopic(), packet, (long)packet.getOpCode().getAsInt());
                            }).activate(executor),
                    // Tx
                    listen().setPredicate(e -> !Objects.requireNonNull(e.getData()).isReceived())
                            .subscribe(e -> {
                                var packet = e.getData();
                                assert packet != null;
                                if (packet.getOpCode() == null && e.getFlag() != null)
                                    packet.setOpCode(IntegerAttribute.valueOf((int)(long)e.getFlag(), GatewayPacket.OpCode.class).assertion());
                                output.println(packet
                                        .setTopic(e.getKey())
                                        .toSerializedString());
                            }),
                    io, output);
        }

        @SneakyThrows
        private GatewayPacket parsePacket(String str) {
            return GatewayPacket.mapper.readValue(str, GatewayPacket.class);
        }

        @Deprecated
        public Predicate<Event<GatewayPacket>> with(final @Nullable String key, final GatewayPacket.OpCode type) {
            return ((Predicate<Event<GatewayPacket>>) (e -> Objects.equals(e.getKey(), key)))
                    .and(e -> Objects.requireNonNull(e.getData()).getOpCode() == type);
        }

        GatewayPacket.Builder packet() {
            return GatewayPacket.builder().connectionId(getUuid());
        }

        GatewayPacket.Builder op(GatewayPacket.OpCode type) {
            return packet().opCode(type);
        }

        GatewayPacket.Builder connect(GatewayConnectionData connectionData) {
            return op(GatewayPacket.OpCode.Connect).data(serialize(connectionData));
        }

        GatewayPacket.Builder data(Object data) {
            return op(GatewayPacket.OpCode.Data).data(serialize(data));
        }
    }
}
