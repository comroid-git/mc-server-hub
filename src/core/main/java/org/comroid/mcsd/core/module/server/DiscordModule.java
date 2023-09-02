package org.comroid.mcsd.core.module.server;

import org.comroid.mcsd.core.entity.Server;
import org.comroid.mcsd.core.module.AbstractModule;

public abstract class DiscordModule extends AbstractModule {
    public DiscordModule(Server server) {
        super(server);
    }
}
