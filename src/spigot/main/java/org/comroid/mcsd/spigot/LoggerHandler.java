package org.comroid.mcsd.spigot;

import lombok.Value;
import org.comroid.api.net.Rabbit;
import org.comroid.mcsd.api.dto.comm.ConsoleData;

import java.util.logging.Handler;
import java.util.logging.LogRecord;

@Value
public class LoggerHandler extends Handler {
    MCSD_Spigot plugin;
    Rabbit.Exchange.Route<ConsoleData> route;

    public LoggerHandler(MCSD_Spigot plugin) {
        this.plugin = plugin;
        this.route = plugin.getConsole().route(MCSD_Spigot.RouteConsoleOutputBase.formatted(plugin.getServerId()), ConsoleData.class);
    }

    @Override
    public void publish(LogRecord record) {
        if (record.getLevel().intValue() < plugin.getConsoleLevel().intValue())
            return;
        var data = ConsoleData.output("[%s]: %s".formatted(record.getLevel().getName(), record.getMessage()));
        route.send(data);
    }

    @Override
    public void flush() {
        // stub; no need to flush
    }

    @Override
    public void close() throws SecurityException {
        // stub; no need to close
    }
}
