package org.comroid.mcsd.connector.gateway;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.SneakyThrows;
import org.comroid.api.Container;
import org.comroid.api.DelegateStream;
import org.comroid.api.Event;
import org.comroid.api.Startable;
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

    protected final class ConnectionHandler extends Container.Base {
        final UUID uuid = UUID.randomUUID();

        @SneakyThrows
        public ConnectionHandler(Socket socket) {
            final var output = new DelegateStream.Output(socket.getOutputStream())
                    //.compress()
                    //.encrypt(getConnectionData().toCipher(Cipher.ENCRYPT_MODE))
                    .toPrintStream();
            addChildren(
                    // Rx
                    new DelegateStream.Input(socket.getInputStream())
                            //.decompress()
                            //.decrypt(getConnectionData().toCipher(Cipher.DECRYPT_MODE))
                            .setEndlMode(DelegateStream.EndlMode.OnNewLine)
                            .subscribe(str -> {
                                var packet = parsePacket(str).setReceived(true);
                                publish(packet.getTopic(), packet);
                            }).activate(executor),
                    // Tx
                    listen(e -> !e.getData().isReceived(),
                            e -> output.println(e.getData()
                                    .setTopic(e.getKey())
                                    .toSerializedString())),
                    output);
        }

        @SneakyThrows
        private GatewayPacket parsePacket(String str) {
            return GatewayPacket.mapper.readValue(str, GatewayPacket.class);
        }

        public Predicate<Event<GatewayPacket>> with(final @Nullable String key, final GatewayPacket.Type type) {
            return ((Predicate<Event<GatewayPacket>>) (e -> Objects.equals(e.getKey(), key)))
                    .and(e -> e.getData().type == type);
        }

        GatewayPacket.Builder packet() {
            return GatewayPacket.builder().connectionId(getUuid());
        }

        GatewayPacket.Builder empty(GatewayPacket.Type type) {
            return packet().type(type);
        }

        GatewayPacket.Builder connect(GatewayConnectionData connectionData) {
            return empty(GatewayPacket.Type.Connect).data(serialize(connectionData));
        }

        GatewayPacket.Builder data(Object data) {
            return empty(GatewayPacket.Type.Data).data(serialize(data));
        }
    }
}
