package org.comroid.mcsd.core.module;

import org.comroid.api.DelegateStream;
import org.comroid.mcsd.core.entity.Server;

public abstract class ShellModule extends ServerModule {
    public ShellModule(Server parent) {
        super(parent);
    }

    public abstract DelegateStream.IO execute(String... command);
}
