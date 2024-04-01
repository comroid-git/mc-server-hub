package org.comroid.mcsd.api.log;

import lombok.Value;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.comroid.api.net.Rabbit;
import org.comroid.mcsd.api.dto.comm.ConsoleData;

import java.util.Calendar;

import static org.comroid.mcsd.api.dto.comm.ConsoleData.output;

@Value
@Plugin(name = "MCSD Console Relay", category = "Core", elementType = "appender", printObject = true)
public class RabbitConsoleAppender extends AbstractAppender {
    Rabbit.Exchange.Route<ConsoleData> route;
    Level level;

    public RabbitConsoleAppender(Rabbit.Exchange.Route<ConsoleData> route, Level level) {
        super("MCSD Console Appender", null, null, false, null);
        this.route = route;
        this.level = level;

        ((Logger) LogManager.getRootLogger()).addAppender(this);
    }

    @Override
    public void append(LogEvent event) {
        try {
            if (event.getLevel().intLevel() > level.intLevel())
                return;
            var calendar = Calendar.getInstance();
            calendar.setTimeInMillis(event.getTimeMillis());
            var str = "[%tT %s] [%s]: %s\n".formatted(
                    calendar,
                    event.getLevel().name(),
                    event.getLoggerName(),
                    event.getMessage().getFormattedMessage());
            route.send(output(str));
        } catch (Throwable t) {
            System.err.println("Error in RabbitAppender");
            t.printStackTrace(System.err);
        }
    }
}
