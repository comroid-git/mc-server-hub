package org.comroid.mcsd.core.module.discord;

import org.comroid.mcsd.core.entity.Server;
import org.comroid.mcsd.core.module.AbstractModule;

import java.util.Optional;

public abstract class DiscordModule extends AbstractModule {
    protected final DiscordAdapter adapter;

    public DiscordModule(Server server) {
        super(server);
        this.adapter = Optional.ofNullable(server.getDiscordBot())
                .map(DiscordAdapter::get)
                .orElseThrow();
    }
}
