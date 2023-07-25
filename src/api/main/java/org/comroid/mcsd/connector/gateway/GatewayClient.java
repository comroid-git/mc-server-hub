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
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.IntSupplier;
import java.util.function.IntUnaryOperator;

@Log
@Data
@RequiredArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class GatewayClient extends GatewayActor {
    private static final IntUnaryOperator retryDelayFunc = x -> x * 2;
    private final HubConnector connector;
    private ConnectionHandler handler;
    private Socket socket;
    private InetSocketAddress endpoint;
    private int retryAttempt;

    @Override
    public GatewayConnectionInfo getConnectionData(UUID $) {
        return connector.getConnectionData();
    }

    @Override
    @SneakyThrows
    public void start() {
        var hubBaseUrl = connector.getConnectionData().getHubBaseUrl();
        if (hubBaseUrl == null) hubBaseUrl = HubConnector.DefaultBaseUrl;
        this.socket = new Socket(hubBaseUrl, HubConnector.Port);
        this.endpoint = InetSocketAddress.createUnresolved(hubBaseUrl, HubConnector.Port);

        tryConnect();
    }

    @SneakyThrows
    private void tryConnect() {
        if (socket.isConnected())
            return;
        try {
            socket.connect(endpoint);
            handler = handle(socket);
            addChildren(socket);

            listen().setKey("close").once().thenRun(this::close);
            publish("connect", handler.connect(connector.getConnectionData()).build());
            retryAttempt = 0;
        } catch (Throwable t) {
            var delay = retryDelayFunc.applyAsInt(retryAttempt);
            log.warning("Unable to connect; retrying in %d seconds".formatted(delay));
            connector.getExecutor().schedule(this::tryConnect, delay, TimeUnit.SECONDS);
        }
    }

    @Override
    public void closeSelf() {
        publish("close", handler.op(GatewayPacket.OpCode.Close).build());
        super.closeSelf();
    }

    @Event.Subscriber
    public void handshake(GatewayPacket packet) {
        log.fine("Handshake with %s successful".formatted(packet.parse(UUID.class)));
    }

    @Event.Subscriber
    public void heartbeat(Event<GatewayPacket> event) {
        if (!Objects.requireNonNull(event.getData()).isHeartbeatValid())
            close();
        publish("heartbeat", handler.data(handler).build());
    }
}
