package org.comroid.mcsd.core.module.internal.side.agent;

import lombok.extern.java.Log;
import org.comroid.api.net.Rabbit;
import org.comroid.mcsd.api.dto.PlayerEvent;
import org.comroid.mcsd.core.ServerManager;
import org.comroid.mcsd.core.dto.ConsoleData;
import org.comroid.mcsd.core.module.InternalModule;
import org.comroid.mcsd.core.module.console.ConsoleModule;
import org.comroid.mcsd.core.module.player.PlayerEventModule;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

import static org.comroid.api.Polyfill.exceptionLogger;
import static org.comroid.mcsd.core.util.ApplicationContextProvider.bean;

/** forwards console output to rabbitmq, and forward rabbitmq input to console */
@Log
public class RabbitLinkModule extends InternalModule {
    @Nullable
    @Inject(required = false)
    private ConsoleModule<?> console;
    @Nullable
    @Inject(required = false)
    private PlayerEventModule<?> players;

    public RabbitLinkModule(ServerManager.Entry entry) {
        super(entry);
    }

    @Override
    protected void $initialize() {
        final var rabbit = bean(Rabbit.class);
        if (console != null) {
            Rabbit.Binding<ConsoleData> inputBinding, outputBinding;
            addChildren(
                    inputBinding = rabbit.bind("mcsd.server." + server.getId(), "module.console.input", ConsoleData.class),
                    outputBinding = rabbit.bind("mcsd.server." + server.getId(), "module.console.output", ConsoleData.class),

                    // rabbit -> console
                    inputBinding
                            .filterData(cdat -> cdat.getType() == ConsoleData.Type.input)
                            .mapData(ConsoleData::getData)
                            .subscribeData(cmd -> console.execute(cmd).exceptionally(
                                    exceptionLogger(log, "Could not forward command '" + cmd + "' from Rabbit to Console"))),
                    // console -> rabbit
                    console.getBus()
                            //.filter(e -> DelegateStream.IO.EventKey_Output.equals(e.getKey()))
                            .mapData(str -> new ConsoleData(ConsoleData.Type.output, str))
                            .subscribeData(outputBinding::send)
            );
        }
        if (players != null) {
            // events -> rabbit
            Arrays.stream(PlayerEvent.Type.values()).forEach(type -> {
                Rabbit.Binding<PlayerEvent> binding;
                addChildren(
                        binding = rabbit.bind("mcsd.server." + server.getId(), "module.player.event." + type.name().toLowerCase(), PlayerEvent.class),
                        players.getBus().filterData(e -> e.getType() == type).subscribeData(binding::send)
                );
            });
        }
    }
}
