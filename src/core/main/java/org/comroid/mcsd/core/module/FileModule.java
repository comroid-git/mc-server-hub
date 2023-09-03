package org.comroid.mcsd.core.module;

import lombok.Value;
import org.comroid.mcsd.core.entity.Server;

@Value
public class FileModule extends ServerModule {
    public static final Factory<FileModule> Factory = new Factory<>(FileModule.class) {
        @Override
        public FileModule create(Server server) {
            return new FileModule(server);
        }
    };

    private FileModule(Server server) {
        super(server);
    }
}
