package org.comroid.mcsd.core.module.remote.rabbit;

import org.comroid.mcsd.core.entity.module.remote.rabbit.RabbitTxModulePrototype;
import org.comroid.mcsd.core.entity.server.Server;
import org.comroid.mcsd.core.module.ServerModule;

public class RabbitTxModule extends ServerModule<RabbitTxModulePrototype> {
    public RabbitTxModule(Server server, RabbitTxModulePrototype proto) {
        super(server, proto);
    }
}
