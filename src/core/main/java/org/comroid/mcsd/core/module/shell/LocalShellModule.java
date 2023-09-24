package org.comroid.mcsd.core.module.shell;

import org.comroid.api.DelegateStream;
import org.comroid.mcsd.core.entity.Server;

public class LocalShellModule extends ShellModule {
    public LocalShellModule(Server parent) {
        super(parent);
    }

    @Override
    public DelegateStream.IO execute(String... command) {
        return DelegateStream.IO.execute(command);
    }
}
