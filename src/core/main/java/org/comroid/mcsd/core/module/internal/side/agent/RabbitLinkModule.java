package org.comroid.mcsd.core.module.internal.side.agent;

import lombok.extern.java.Log;
import org.comroid.api.net.Rabbit;
import org.comroid.mcsd.core.ServerManager;
import org.comroid.mcsd.core.dto.ConsoleData;
import org.comroid.mcsd.core.module.console.ConsoleModule;
import org.comroid.mcsd.core.module.InternalModule;

import static org.comroid.api.Polyfill.exceptionLogger;
import static org.comroid.mcsd.core.util.ApplicationContextProvider.bean;

/** forwards console output to rabbitmq, and forward rabbitmq input to console */
@Log
public class RabbitLinkModule extends InternalModule {
    private @Inject ConsoleModule<?> console;

    public RabbitLinkModule(ServerManager.Entry entry) {
        super(entry);
    }

    @Override
    protected void $initialize() {
        Rabbit.Binding<ConsoleData> inputBinding, outputBinding;
        addChildren(
                inputBinding = bean(Rabbit.class).bind("mcsd."+server.getId(), "module.console.input", ConsoleData.class),
                outputBinding = bean(Rabbit.class).bind("mcsd."+server.getId(), "module.console.output", ConsoleData.class),
                // rabbit -> console
                inputBinding
                        .filterData(cdat -> cdat.getType() == ConsoleData.Type.input)
                        .mapData(ConsoleData::getData)
                        .subscribeData(cmd -> console.execute(cmd).exceptionally(
                                exceptionLogger(log, "Could not forward command '"+cmd+"' from Rabbit to Console"))),
                // console -> rabbit
                console.getBus()
                        //.filter(e -> DelegateStream.IO.EventKey_Output.equals(e.getKey()))
                        .mapData(str -> new ConsoleData(ConsoleData.Type.output, str))
                        .subscribeData(outputBinding::send)
        );
    }
}
