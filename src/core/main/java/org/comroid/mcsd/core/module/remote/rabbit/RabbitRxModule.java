package org.comroid.mcsd.core.module.remote.rabbit;

import org.comroid.mcsd.core.entity.module.remote.rabbit.RabbitRxModulePrototype;
import org.comroid.mcsd.core.entity.server.Server;
import org.comroid.mcsd.core.module.console.ConsoleModule;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

public class RabbitRxModule extends ConsoleModule<RabbitRxModulePrototype> {
    public RabbitRxModule(Server server, RabbitRxModulePrototype proto) {
        super(Direction.Output, server, proto);
    }

    @Override
    public CompletableFuture<String> execute(String input, @Nullable Pattern terminator) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException());
    }
}
