package org.comroid.mcsd.core.module.status;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.FieldDefaults;
import lombok.extern.java.Log;
import org.apache.commons.lang3.NotImplementedException;
import org.comroid.api.Event;
import org.comroid.mcsd.api.model.IStatusMessage;
import org.comroid.mcsd.api.model.Status;
import org.comroid.mcsd.core.entity.Server;
import org.comroid.mcsd.core.module.ServerModule;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import static org.comroid.mcsd.core.util.ApplicationContextProvider.bean;

@Log
@Getter
@ToString
@FieldDefaults(level = AccessLevel.PRIVATE)
public class StatusModule extends ServerModule {
    public static final Factory<StatusModule> Factory = new Factory<>(StatusModule.class) {
        @Override
        public StatusModule create(Server server) {
            return new StatusModule(server);
        }
    };

    IStatusMessage previousStatus = Status.unknown_status;
    IStatusMessage currentStatus = Status.unknown_status;

    Event.Bus<IStatusMessage> bus;

    private StatusModule(Server server) {
        super(server);
    }

    @Override
    protected void $initialize() {
        addChildren(bus = new Event.Bus<>());
    }

    public CompletableFuture<IStatusMessage> pushStatus(final IStatusMessage message) {
        return CompletableFuture.supplyAsync(() -> {
                    if (currentStatus != null && currentStatus.getStatus() == message.getStatus()
                            && Objects.equals(currentStatus.getMessage(), message.getMessage()))
                        return CompletableFuture.completedFuture(currentStatus); // do not push same status twice
                    previousStatus = currentStatus;
                    currentStatus = message;
                    return message;
                }).thenCompose(msg -> server.component(UptimeModule.class)
                        .ifPresentMapOrElseGet(UptimeModule::pushUptime, () -> CompletableFuture.completedFuture(null)))
                .thenApply($ -> {
                    bus.publish(message);
                    return message;
                });
    }

    public CompletableFuture<IStatusMessage> getStatus() {
        throw new NotImplementedException(); // todo
    }
}