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
    private Command.Manager cmdr;
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

    @Override
    @SneakyThrows
    public void start() {
        if (!currentBackup.get().isDone() || updateRunning.get())
            return;
        if (getState() == State.Running)
            return;



        var botConId = server.getDiscordBot();
        if (botConId != null)
            addChildren(discord = new DiscordConnection(this));
        pushStatus(Status.starting);


        this.cmdr = new Command.Manager(this);
        addChildren(cmdr);
        listenForPattern(McsdPattern).subscribeData(this::runCommand);

        if (Debug.isDebug())
            //oe.redirectToLogger(log);
            oe.redirectToSystem();
    }

    //region Commands
    private void runCommand(Matcher matcher) {
        var username = matcher.group("username");
        var profile = bean(MinecraftProfileRepo.class).get(username);
        var command = matcher.group("command");
        cmdr.execute(command.replaceAll("\r?\n", ""), profile);
    }

    @Override
    public void handleResponse(Command.Delegate cmd, @NotNull Object response, Object... args) {
        var profile = Arrays.stream(args)
                .flatMap(Streams.cast(MinecraftProfile.class))
                .findAny()
                .orElseThrow();
        var tellraw = Tellraw.Command.builder()
                .selector(profile.getName())
                .component(McFormatCode.Gray.text("<").build())
                .component(McFormatCode.Light_Purple.text("mcsd").build())
                .component(McFormatCode.Gray.text("> ").build())
                .component(McFormatCode.Reset.text(response.toString()).build())
                .build()
                .toString();
        in.println(tellraw); // todo: tellraw data often too long
        log.trace(tellraw);
        in.flush();
    }

    @Command
    public String link(MinecraftProfile profile) {
        final var profiles = bean(MinecraftProfileRepo.class);
        String code;
        do {
            code = Token.random(6, false);
        } while (profiles.findByVerification(code).isPresent());
        profile.setVerification(code);
        profiles.save(profile);
        return "Please run this command on discord: /verify " + code;
    }
    //endregion


