package org.comroid.mcsd.core.module.shell;

import org.comroid.api.DelegateStream;
import org.comroid.mcsd.core.entity.Server;
import org.comroid.mcsd.core.module.ServerModule;

public abstract class ShellModule extends ServerModule {
    public ShellModule(Server parent) {
        super(parent);
    }

    public abstract DelegateStream.IO execute(String... command);
}
