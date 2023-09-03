package org.comroid.mcsd.agent;

import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.comroid.api.*;
import org.comroid.api.io.FileHandle;
import org.comroid.api.os.OS;
import org.comroid.mcsd.api.model.IStatusMessage;
import org.comroid.mcsd.api.model.Status;
import org.comroid.mcsd.core.entity.Backup;
import org.comroid.mcsd.core.entity.MinecraftProfile;
import org.comroid.mcsd.core.entity.Server;
import org.comroid.mcsd.core.entity.ServerUptimeEntry;
import org.comroid.mcsd.core.module.ExecutionModule;
import org.comroid.mcsd.core.repo.BackupRepo;
import org.comroid.mcsd.core.repo.MinecraftProfileRepo;
import org.comroid.mcsd.core.repo.ServerRepo;
import org.comroid.mcsd.core.repo.ServerUptimeRepo;
import org.comroid.mcsd.util.McFormatCode;
import org.comroid.mcsd.util.Tellraw;
import org.comroid.util.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.net.URL;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntFunction;
import java.util.regex.Matcher;

import static org.comroid.mcsd.core.util.ApplicationContextProvider.bean;

@Slf4j
@Getter
@Deprecated
public class ServerProcess extends ExecutionModule implements Startable, Command.Handler {
    // todo: improve these
    private final AtomicReference<CompletableFuture<@Nullable File>> currentBackup = new AtomicReference<>(CompletableFuture.completedFuture(null));
    private final AtomicBoolean updateRunning = new AtomicBoolean(false);
    private final AtomicInteger lastTicker = new AtomicInteger(0);
    private final @Deprecated @lombok.experimental.Delegate Event.Bus<String> bus = new Event.Bus<>();
    private final AgentRunner runner;
    private IStatusMessage previousStatus = Status.unknown_status;
    private IStatusMessage currentStatus = Status.unknown_status;

    public ServerProcess(AgentRunner runner, Server server) {
        super(server);
        this.runner = runner;
    }

    public State getState() {
        return process == null
                ? State.NotStarted
                : process.isAlive()
                ? State.Running
                : State.Exited;
    }

    public void pushStatus(IStatusMessage message) {
        if (currentStatus != null && currentStatus.getStatus() == message.getStatus()
                && Objects.equals(currentStatus.getMessage(), message.getMessage()))
            return; // do not push same status twice
        previousStatus = currentStatus;
        currentStatus = message;
        bean(ServerRepo.class).setStatus(server.getId(), message.getStatus());
        bean(Event.Bus.class, "eventBus").publish(server.getId().toString(), message);
        pushUptime();
    }

    public void pushUptime() {
    }

    public boolean pushMaintenance(boolean val) { //todo
        var is = server.isMaintenance();
        runner.getServers().setMaintenance(server.getId(), val);
        if (is && !val) {
            // disable maintenance
            in.println("whitelist off");
            pushStatus(Status.in_maintenance_mode.new Message("Maintenance has been turned off"));
        } else if (!is && val) {
            // enable maintenance
            in.println("whitelist on");
            pushStatus(Status.in_maintenance_mode);
        }
        return is != val;
    }


    //region Commands
    private void runCommand(Matcher matcher) {
    }

    //endregion


