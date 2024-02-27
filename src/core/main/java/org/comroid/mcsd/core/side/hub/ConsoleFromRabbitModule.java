package org.comroid.mcsd.core.side.hub;

import org.comroid.api.net.Rabbit;
import org.comroid.mcsd.core.ServerManager;
import org.comroid.mcsd.core.dto.ConsoleData;
import org.comroid.mcsd.core.entity.module.console.ConsoleModulePrototype;
import org.comroid.mcsd.core.module.console.ConsoleModule;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

import static org.comroid.mcsd.core.dto.ConsoleData.input;
import static org.comroid.mcsd.core.util.ApplicationContextProvider.bean;

/** inits console bus with rabbitmq connection and sends commands through rabbitmq */
public class ConsoleFromRabbitModule extends ConsoleModule<@Nullable ConsoleModulePrototype> {
    public ConsoleFromRabbitModule(ServerManager.Entry entry) {
        super(Direction.Bidirectional, entry.getServer(), null);
    }

    @Override
    protected void $initialize() {
        Rabbit.Binding<ConsoleData> binding;
        addChildren(
                binding = bean(Rabbit.class).bind("mcsd."+server.getId(), "module.console.output", ConsoleData.class),

                // rabbit -> console
                bus = binding
                        .filterData(cData -> cData.getType() == ConsoleData.Type.input)
                        .mapData(ConsoleData::getData)
        );
    }

    @Override
    public CompletableFuture<@Nullable String> execute(String input, @Nullable Pattern terminator) {
        return CompletableFuture.supplyAsync(() -> {
            bean(Rabbit.class).bind("mcsd."+server.getId(),"module.console.input", ConsoleData.class)
                    .send(input(input));
            return null;
        });
    }
}
