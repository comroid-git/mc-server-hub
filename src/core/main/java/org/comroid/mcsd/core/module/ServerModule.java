package org.comroid.mcsd.core.module;

import lombok.Data;
import org.comroid.api.Component;
import org.comroid.api.Named;
import org.comroid.mcsd.core.entity.Server;
import org.jetbrains.annotations.Nullable;

public abstract class ServerModule extends Component.Sub<Server> implements Named {
    public ServerModule(Server parent) {
        super(parent);
    }

    @Data
    public static abstract class Factory<Module extends ServerModule> {
        protected final Class<Module> type;

        public abstract Module create(Server parent);
    }
}
