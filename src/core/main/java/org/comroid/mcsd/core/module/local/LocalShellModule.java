package org.comroid.mcsd.core.module.local;

import org.comroid.api.DelegateStream;
import org.comroid.mcsd.core.entity.Server;
import org.comroid.mcsd.core.module.ShellModule;

public class LocalShellModule extends ShellModule {
    public static final Factory<LocalShellModule> Factory = new Factory<>(LocalShellModule.class) {
        @Override
        public LocalShellModule create(Server parent) {
            return new LocalShellModule(parent);
        }
    };
    public LocalShellModule(Server parent) {
        super(parent);
    }

    @Override
    public DelegateStream.IO execute(String... command) {
        return DelegateStream.IO.execute(command);
    }
}
