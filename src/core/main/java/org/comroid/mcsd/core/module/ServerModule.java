package org.comroid.mcsd.core.module;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.comroid.api.Component;
import org.comroid.api.Named;
import org.comroid.mcsd.core.ServerManager;
import org.comroid.mcsd.core.entity.module.ModulePrototype;
import org.comroid.mcsd.core.entity.server.Server;
import org.jetbrains.annotations.Nullable;

import static org.comroid.mcsd.core.util.ApplicationContextProvider.bean;

@Getter
@AllArgsConstructor
public abstract class ServerModule<T extends ModulePrototype> extends Component.Base implements Named {
    protected final Server server;
    protected T proto;

    @Override
    public @Nullable Component getParent() {
        return bean(ServerManager.class).tree(server);
    }

    @Override
    public boolean isEnabled() {
        return proto.isEnabled();
    }

    @Override
    public boolean isSubComponent() {
        return true;
    }
}
