package org.comroid.mcsd.core.module;

import lombok.Value;
import org.apache.commons.lang3.NotImplementedException;
import org.comroid.mcsd.core.entity.Server;

import java.util.concurrent.CompletableFuture;

@Value
public class BackupModule extends ServerModule {
    public static final Factory<BackupModule> Factory = new Factory<>(BackupModule.class) {
        @Override
        public BackupModule create(Server server) {
            return new BackupModule(server);
        }
    };

    private BackupModule(Server server) {
        super(server);
    }

    public CompletableFuture<Boolean> runBackup() {
        throw new NotImplementedException(); // todo
    }
}
