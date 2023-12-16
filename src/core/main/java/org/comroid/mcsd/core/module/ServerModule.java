package org.comroid.mcsd.core.module;

import lombok.AllArgsConstructor;
import org.comroid.api.Component;
import org.comroid.api.Named;
import org.comroid.mcsd.core.ServerManager;
import org.comroid.mcsd.core.entity.module.ModulePrototype;
import org.comroid.mcsd.core.entity.server.Server;
import org.jetbrains.annotations.Nullable;

import static org.comroid.mcsd.core.util.ApplicationContextProvider.bean;

@AllArgsConstructor
public abstract class ServerModule<T extends ModulePrototype> extends Component.Base implements Named {
    protected final Server server;
    protected final T proto;

    @Override
    public @Nullable Component getParent() {
        return bean(ServerManager.class).tree(server);
    }
}
