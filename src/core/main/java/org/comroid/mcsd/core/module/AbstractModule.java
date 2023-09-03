package org.comroid.mcsd.core.module;

import lombok.Data;
import org.comroid.api.Component;
import org.comroid.api.Named;
import org.comroid.mcsd.core.entity.Server;
import org.jetbrains.annotations.Nullable;

@Data
public abstract class AbstractModule extends Component.Base implements Named {
    protected final Server server;

    @Override
    public final @Nullable Component getParent() {
        return server;
    }

    @Override
    public final Component.Base setParent(@Nullable Component parent) {
        throw new UnsupportedOperationException();
    }
}
