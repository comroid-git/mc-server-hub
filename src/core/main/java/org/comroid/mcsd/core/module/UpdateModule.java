package org.comroid.mcsd.core.module;

import lombok.Value;
import org.apache.commons.lang3.NotImplementedException;
import org.comroid.mcsd.core.entity.Server;

import java.util.concurrent.CompletableFuture;

@Value
public class UpdateModule extends ServerModule {
    public static final Factory<UpdateModule> Factory = new Factory<>(UpdateModule.class) {
        @Override
        public UpdateModule create(Server server) {
            return new UpdateModule(server);
        }
    };

    private UpdateModule(Server server) {
        super(server);
    }

    public CompletableFuture<Boolean> runUpdate() {
        throw new NotImplementedException(); // todo
    }
}
