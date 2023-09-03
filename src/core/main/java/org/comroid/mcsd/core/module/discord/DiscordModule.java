package org.comroid.mcsd.core.module.discord;

import org.comroid.mcsd.core.entity.Server;
import org.comroid.mcsd.core.module.ServerModule;
import org.comroid.mcsd.core.module.UpdateModule;

import java.util.Optional;

public class DiscordModule extends ServerModule {
    public static final Factory<DiscordModule> Factory = new Factory<>(DiscordModule.class) {
        @Override
        public DiscordModule create(Server server) {
            return new DiscordModule(server);
        }
    };
    protected final DiscordAdapter adapter;

    public DiscordModule(Server server) {
        super(server);
        this.adapter = Optional.ofNullable(server.getDiscordBot())
                .map(DiscordAdapter::get)
                .orElseThrow();
    }
}
