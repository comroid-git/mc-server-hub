package org.comroid.mcsd.core.module;

import lombok.Value;
import lombok.experimental.NonFinal;
import org.comroid.mcsd.core.ServerManager;
import org.comroid.mcsd.core.entity.module.InternalModulePrototype;
import org.comroid.mcsd.core.model.IInternalModule;
import org.jetbrains.annotations.Nullable;

@Value @NonFinal
public abstract class InternalModule extends ServerModule<@Nullable InternalModulePrototype> implements IInternalModule {
    ServerManager.Entry managerEntry;

    public InternalModule(ServerManager.Entry managerEntry) {
        super(managerEntry.getServer(), null);
        this.managerEntry = managerEntry;
    }
}
