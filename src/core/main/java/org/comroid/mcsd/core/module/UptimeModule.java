package org.comroid.mcsd.core.module;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import lombok.extern.java.Log;
import org.apache.commons.lang3.NotImplementedException;
import org.comroid.api.Event;
import org.comroid.mcsd.api.model.IStatusMessage;
import org.comroid.mcsd.api.model.Status;
import org.comroid.mcsd.core.entity.Server;
import org.comroid.mcsd.core.repo.ServerRepo;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import static org.comroid.mcsd.core.util.ApplicationContextProvider.bean;

@Log
@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UptimeModule extends ServerModule {
    public static final Factory<UptimeModule> Factory = new Factory<>(UptimeModule.class) {
        @Override
        public UptimeModule create(Server server) {
            return new UptimeModule(server);
        }
    };

    private UptimeModule(Server server) {
        super(server);
    }

    public CompletableFuture<Void> pushUptime() {
        throw new NotImplementedException(); // todo
    }
}
