package org.comroid.mcsd.core.module;

import lombok.Data;
import org.comroid.api.Container;
import org.comroid.api.Named;
import org.comroid.mcsd.core.entity.Server;

@Data
public abstract class AbstractModule extends Container.Base implements Named, Runnable {
    private final Server server;
}
