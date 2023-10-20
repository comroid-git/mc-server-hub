package org.comroid.mcsd.core.module;

import lombok.Data;
import org.comroid.api.Component;
import org.comroid.api.Named;
import org.comroid.mcsd.core.ServerManager;
import org.comroid.mcsd.core.entity.Server;
import org.comroid.mcsd.core.util.ApplicationContextProvider;
import org.jetbrains.annotations.Nullable;

import static org.comroid.mcsd.core.util.ApplicationContextProvider.bean;

public abstract class ServerModule extends Component.Base implements Named {
    protected final Server server;

    @Override
    public @Nullable Component getParent() {
        return bean(ServerManager.class).tree(server);
    }

    public ServerModule(Server server) {
        this.server = server;
    }

    @Data
    public static abstract class Factory<Module extends ServerModule> {
        protected final Class<Module> type;

        public abstract Module create(Server parent);
    }
}
