package org.comroid.mcsd.core.module;

import lombok.Data;
import org.comroid.api.Component;
import org.comroid.api.Named;
import org.comroid.mcsd.core.entity.Server;
import org.jetbrains.annotations.Nullable;

@Data
public abstract class ServerModule extends Component.Base implements Named {
    protected final Server server;

    @Override
    public final @Nullable Component getParent() {
        return server;
    }

    @Override
    public final Component.Base setParent(@Nullable Component parent) {
        return this; // do nothing, parent is final
    }

    @Data
    public static abstract class Factory<Module extends ServerModule> {
        protected final Class<Module> type;

        public abstract Module create(Server server);
    }
}
