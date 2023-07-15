package org.comroid.mcsd.connector.gateway;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import org.comroid.api.os.OS;
import org.comroid.mcsd.connector.HubConnector;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.UnknownHostException;

@Data
@Component
@NoArgsConstructor(force = true)
@EqualsAndHashCode(callSuper = true)
public class GatewayServer extends GatewayActor implements Runnable {
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
        while (server.isBound())
            handle(server.accept());
    }
}
