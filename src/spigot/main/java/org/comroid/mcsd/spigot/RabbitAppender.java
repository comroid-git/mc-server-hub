package org.comroid.mcsd.spigot;

import lombok.Value;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.filter.AbstractFilter;
import org.apache.logging.log4j.core.filter.DenyAllFilter;
import org.apache.logging.log4j.core.layout.MessageLayout;
import org.apache.logging.log4j.message.Message;
import org.comroid.api.net.Rabbit;
import org.comroid.mcsd.api.dto.comm.ConsoleData;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Calendar;

import static org.comroid.mcsd.api.dto.comm.ConsoleData.output;

@Value
@Plugin(name = "MCSD Console Relay", category = "Core", elementType = "appender", printObject = true)
public class RabbitAppender extends AbstractAppender {
    MCSD_Spigot plugin;
    Rabbit.Exchange.Route<ConsoleData> route;

    public RabbitAppender(MCSD_Spigot plugin) {
        super("MCSD Console Appender", null, null, false, null);
        this.plugin = plugin;
        this.route = plugin.getConsole().route(MCSD_Spigot.RouteConsoleOutputBase.formatted(plugin.getServerId()), ConsoleData.class);

        ((Logger) LogManager.getRootLogger()).addAppender(this);
    }

    @Override
    public void append(LogEvent event) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(event.getTimeMillis());
        var str = "[%tT %s] %s: %s".formatted(
                calendar,
                event.getLevel().name(),
                event.getLoggerName(),
                event.getMessage().getFormattedMessage());
        route.send(output(str));
    }
}
