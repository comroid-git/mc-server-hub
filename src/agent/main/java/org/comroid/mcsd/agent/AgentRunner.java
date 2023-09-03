package org.comroid.mcsd.agent;

import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.java.Log;
import org.comroid.api.Command;
import org.comroid.api.DelegateStream;
import org.comroid.api.Event;
import org.comroid.api.Polyfill;
import org.comroid.mcsd.agent.controller.ConsoleController;
import org.comroid.mcsd.api.model.Status;
import org.comroid.mcsd.core.entity.*;
import org.comroid.mcsd.core.module.*;
import org.comroid.mcsd.core.repo.ServerRepo;
import org.comroid.mcsd.core.repo.ShRepo;
import org.comroid.mcsd.util.Utils;
import org.comroid.util.StandardValueType;
import org.comroid.util.Streams;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.io.PrintStream;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.comroid.mcsd.core.util.ApplicationContextProvider.bean;

@Log
@Getter
@Service
public class AgentRunner implements Command.Handler {
    public final Agent me;
    public final DelegateStream.IO oe;
    public final PrintStream out;
    public final PrintStream err;
    public final Command.Manager cmd;
    public Server attached;
    @Autowired
    public Event.Bus<Object> eventBus;
    @Lazy
    @Autowired
    private ServerRepo servers;

    public AgentRunner(@Autowired Agent me) {
        this.me = me;
        this.oe = new DelegateStream.IO(DelegateStream.Capability.Output, DelegateStream.Capability.Error);
        this.out = oe.output().require(o -> new PrintStream(o, true));
        this.err = oe.error().require(e -> new PrintStream(e, true));
        this.cmd = new Command.Manager(this);
    }

    @Command
    public String list() {
        return bean(AgentRunner.class)
                .streamServerStatusMsgs()
                .collect(Collectors.joining("\n\t- ", "Servers:\n\t- ", ""));
    }

    @Command(usage = "<name>")
    public String backup(String[] args, ConsoleController.Connection con) {
        var srv = getServer(args);
        srv.verifyPermission(con.getUser(), AbstractEntity.Permission.Manage)
                .orElseThrow(()->new Command.Error("Insufficient permissions"));

        srv.component(BackupModule.class).assertion()
                .runBackup(true)
                .exceptionally(Polyfill.exceptionLogger());
        return "Backup of "+srv+" started";
    }

    @SneakyThrows
    @Command(usage = "<name> [-f]")
    public String update(String[] args, ConsoleController.Connection con) {
        var srv = getServer(args);
        srv.verifyPermission(con.getUser(), AbstractEntity.Permission.Manage)
                .orElseThrow(()->new Command.Error("Insufficient permissions"));

        var force = args[1].contains("-f");
        return srv.component(UpdateModule.class).assertion()
                .runUpdate(force)
                .join() ? srv + " already up to date" : srv + " updated";
    }

    @Command(usage = "<name>")
    public Object status(String[] args, ConsoleController.Connection con) {
        var srv = getServer(args);
        srv.verifyPermission(con.getUser(), AbstractEntity.Permission.View)
                .orElseThrow(()->new Command.Error("Insufficient permissions"));
        return srv.component(StatusModule.class).assertion()
                .getCurrentStatus();
    }

    @Command(usage = "<name> [-na]")
    public Object enable(String[] args, ConsoleController.Connection con) {
        var srv = getServer(args);
        srv.verifyPermission(con.getUser(), AbstractEntity.Permission.Administrate)
                .orElseThrow(()->new Command.Error("Insufficient permissions"));

        var flags = args.length > 1 ? args[1] : "";
        servers.setEnabled(srv.getId(), true);
        if (flags.contains("n"))
            return start(args, con);
        return srv + " is now enabled";
    }

    @Command(usage = "<name> [-nt]")
    public Object disable(String[] args, ConsoleController.Connection con) {
        var srv = getServer(args);
        srv.verifyPermission(con.getUser(), AbstractEntity.Permission.Administrate)
                .orElseThrow(()->new Command.Error("Insufficient permissions"));

        var flags = args.length > 1 ? args[1] : "";
        servers.setEnabled(srv.getId(), false);
        if (flags.contains("n"))
            return stop(args, con);
        return srv + " is now disabled";
    }

    @Command(usage = "<name> [-a]")
    public Object start(String[] args, ConsoleController.Connection con) {
        var srv = getServer(args);
        srv.verifyPermission(con.getUser(), AbstractEntity.Permission.Manage)
                .orElseThrow(()->new Command.Error("Insufficient permissions"));

        var flags = args.length > 1 ? args[1] : "";
        srv.component(ExecutionModule.class).assertion().start();
        if (flags.contains("a"))
            attach(args, con);
        return srv + " was started";
    }

    @Command(usage = "<name> [time]")
    public Object stop(String[] args, ConsoleController.Connection con) {
        var srv = getServer(args);
        srv.verifyPermission(con.getUser(), AbstractEntity.Permission.Manage)
                .orElseThrow(()->new Command.Error("Insufficient permissions"));

        var timeout = args.length > 2 ? Integer.parseInt(args[2]) : 10;
        srv.component(ExecutionModule.class).assertion()
                .shutdown("Admin shutdown", timeout)
                .thenRun(() -> out.println(srv + " was shut down"));
        return srv + " will shut down in " + timeout + " seconds";
    }

    /* todo
    @Command(usage = "<name> [true/false]")
    public Object maintenance(String[] args, ConsoleController.Connection con) {
        var srv = getServer(args);
        srv.verifyPermission(con.getUser(), AbstractEntity.Permission.Manage)
                .orElseThrow(()->new Command.Error("Insufficient permissions"));

        var val = args.length > 1 ? StandardValueType.BOOLEAN.parse(args[2]) : !srv.isMaintenance();
        return srv + (proc.pushMaintenance(val) ? " was " : " could not be ")
                + (val ? " put into " : " taken out of ") + " Maintenance mode";
    }
     */

    @Command(usage = "<name> <command...>")
    public String execute(String[] args, ConsoleController.Connection con) {
        var srv = getServer(args);
        srv.verifyPermission(con.getUser(), AbstractEntity.Permission.Administrate)
                .orElseThrow(()->new Command.Error("Insufficient permissions"));

        // forward command
        srv.component(ConsoleModule.class).assertion().execute(Arrays.stream(args).skip(1).collect(Collectors.joining(" ")));
        return "Executing command";
    }

    @Command(usage = "<name>")
    public String attach(String[] args, ConsoleController.Connection con) {
        var srv = getServer(args);
        srv.verifyPermission(con.getUser(), AbstractEntity.Permission.Administrate)
                .orElseThrow(()->new Command.Error("Insufficient permissions"));

        if (attached != null)
            con.detach();

        con.attach(srv);
        attached = srv;
        return "Attached to " + srv;
    }

    @Command(usage = "")
    public String detach(String[] args, ConsoleController.Connection con) {
        if (attached == null)
            throw new Command.MildError("Not attached");
        con.detach();
        attached = null;
        return "Detached";
    }

    @Command(usage = "<name> <version> <mode> [-na]")
    public Object create(String[] args, ConsoleController.Connection con) {
        if (Arrays.stream(Utils.SuperAdmins).noneMatch(con.getUser().getId()::equals))
            throw new Command.Error("Insufficient permissions");
        String name = args[0];
        String version = null;
        Server.Mode mode = Arrays.stream(Server.Mode.values())
                .filter(m->m.name().equalsIgnoreCase(args[2]))
                .findAny()
                .orElseThrow();
        ShConnection shCon = bean(ShRepo.class).findById(me.getTarget()).orElseThrow();
        Server server = new Server(
                shCon,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                true,
                true,
                null,
                version,
                shCon.getHost(),
                25565,
                "~/mcsd/"+name,
                mode,
                (byte)4,
                false,
                true,
                false,
                true,
                20,
                25565,
                25575,
                null,
                Duration.ofHours(12),
                Duration.ofDays(7),
                Instant.EPOCH,
                Instant.EPOCH,
                Status.unknown_status,
                new ArrayList<>()
        );
        server.setOwner(con.getUser()).setName(name);
        return servers.save(server) + " created";
    }

    @Command(usage = "")
    public String shutdown(ConsoleController.Connection con) {
        if (Arrays.stream(Utils.SuperAdmins).noneMatch(con.getUser().getId()::equals))
            throw new Command.Error("Insufficient permissions");
        log.info("Shutting down agent");
        System.exit(0);
        return "shutting down";
    }

    private Server getServer(String[] args) {
        var srv = servers.findByAgentAndName(getMe().getId(), args[0]).orElse(null);
        if (srv == null) throw new Command.ArgumentError("name", "Server not found");
        return srv;
    }

    @Override
    public void handleResponse(Command.Delegate cmd, @NotNull Object response, Object... args) {
        out.println(response);
    }

    public Stream<Server> streamServers() {
        return Streams.of(bean(ServerRepo.class).findAllForAgent(getMe().getId()));
    }

    public Stream<String> streamServerStatusMsgs() {
        return streamServers().map(Server::toString);
    }

    public void execute(String cmd, ConsoleController.Connection con) {
        if (!"detach".equals(cmd) && con.getProcess() != null && attached != null)
            con.getProcess().component(ConsoleModule.class).assertion().execute(cmd);
        else getCmd().execute(cmd, con);
    }

    @PreDestroy
    public void close() {
        CompletableFuture.allOf(Streams.of(servers.findAll())
                        .flatMap(srv -> srv.component(ExecutionModule.class).stream())
                        .map(module -> module.shutdown("Host shutdown", 3))
                        .toArray(CompletableFuture[]::new))
                .join();
    }
}
