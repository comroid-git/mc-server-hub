package org.comroid.mcsd.core.module.status;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.FieldDefaults;
import lombok.extern.java.Log;
import org.comroid.api.Component;
import org.comroid.api.os.OS;
import org.comroid.mcsd.core.entity.Server;
import org.comroid.mcsd.core.entity.ServerUptimeEntry;
import org.comroid.mcsd.core.module.local.LocalExecutionModule;
import org.comroid.mcsd.core.module.ServerModule;
import org.comroid.mcsd.core.repo.ServerUptimeRepo;

import java.util.concurrent.CompletableFuture;

import static java.time.Instant.now;
import static org.comroid.mcsd.core.util.ApplicationContextProvider.bean;

@Log
@Getter
@ToString
@FieldDefaults(level = AccessLevel.PRIVATE)
@Component.Requires({LocalExecutionModule.class, StatusModule.class})
public class UptimeModule extends ServerModule {
    public static final Factory<UptimeModule> Factory = new Factory<>(UptimeModule.class) {
        @Override
        public UptimeModule create(Server parent) {
            return new UptimeModule(parent);
        }
    };

    LocalExecutionModule execution;
    StatusModule statusModule;

    private UptimeModule(Server parent) {
        super(parent);
    }

    @Override
    protected void $initialize() {
        execution = component(LocalExecutionModule.class).assertion();
        statusModule = component(StatusModule.class).assertion();
    }

    @Override
    protected void $tick() {
        pushUptime();
    }

    public CompletableFuture<Void> pushUptime() {
        return parent.status()
                .thenCombine(execution.getProcess()!=null&&execution.getProcess().isAlive()
                                ? OS.current.getRamUsage(execution.getProcess().pid())
                                : CompletableFuture.completedFuture(0L),
                        (stat, ram) -> new ServerUptimeEntry(parent,
                                statusModule.getCurrentStatus().getStatus(),
                                stat.getPlayers() != null ? stat.getPlayers().size() : stat.getPlayerCount(),
                                ram))
                .thenAccept(bean(ServerUptimeRepo.class)::save);
    }
}
