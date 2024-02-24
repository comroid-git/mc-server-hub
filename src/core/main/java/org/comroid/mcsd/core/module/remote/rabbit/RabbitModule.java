package org.comroid.mcsd.core.module.remote.rabbit;

import org.comroid.mcsd.core.entity.module.remote.rabbit.RabbitModulePrototype;
import org.comroid.mcsd.core.entity.server.Server;
import org.comroid.mcsd.core.module.console.ConsoleModule;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

public class RabbitModule extends ConsoleModule<RabbitModulePrototype> {
    public RabbitModule(Direction direction, Server server, RabbitModulePrototype proto) {
        super(direction, server, proto);
    }

    @Override
    public CompletableFuture<String> execute(String input, @Nullable Pattern terminator) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException()); // todo
    }
}
