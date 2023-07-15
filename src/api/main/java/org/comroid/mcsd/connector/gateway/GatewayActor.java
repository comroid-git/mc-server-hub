package org.comroid.mcsd.connector.gateway;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.SneakyThrows;
import org.comroid.api.Container;
import org.comroid.api.DelegateStream;
import org.comroid.api.Event;
import org.comroid.api.Startable;

import java.io.PrintStream;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Data
@EqualsAndHashCode(callSuper = true)
public abstract class GatewayActor extends Event.Bus<GatewayPacket> implements Startable {
    protected final ExecutorService executor = Executors.newFixedThreadPool(8);
    private ConnectionHandler handler;

    protected void handle(Socket socket) {
        handler = new ConnectionHandler(socket);
        addChildren(handler);
    }

    protected final class ConnectionHandler extends Container.Base {
        @SneakyThrows
        public ConnectionHandler(Socket socket) {
            final var output = new PrintStream(socket.getOutputStream());
            addChildren(
                    // receive handler
                    new DelegateStream.Input(socket.getInputStream())
                            .setEndlMode(DelegateStream.EndlMode.OnNewLine)
                            .subscribe(str -> {
                                var packet = parsePacket(str).setReceived(true);
                                publish(packet.getTopic(), packet);
                            }).activate(executor),
                    // transmit handler
                    listen(e -> !e.getData().isReceived(),
                            e -> output.println(e.getData()
                                    .setTopic(e.getKey())
                                    .toSerializedString())),
                    // transmitter writer
                    output);
        }

        @SneakyThrows
        private GatewayPacket parsePacket(String str) {
            return GatewayPacket.mapper.readValue(str, GatewayPacket.class);
        }
    }
}
