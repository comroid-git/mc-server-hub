package org.comroid.mcsd.agent;

import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.SneakyThrows;
import org.comroid.api.Command;
import org.comroid.api.DelegateStream;
import org.comroid.api.Event;
import org.comroid.api.Polyfill;
import org.comroid.mcsd.agent.controller.ConsoleController;
import org.comroid.mcsd.agent.discord.DiscordAdapter;
import org.comroid.mcsd.core.entity.Agent;
import org.comroid.mcsd.core.entity.DiscordBot;
import org.comroid.mcsd.core.entity.Server;
import org.comroid.mcsd.core.repo.ServerRepo;
import org.comroid.util.StandardValueType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.comroid.mcsd.core.util.ApplicationContextProvider.bean;

@Getter
@Service
public class AgentRunner implements Command.Handler {
    public final Map<UUID, ServerProcess> processes = new ConcurrentHashMap<>();
    public final Map<UUID, DiscordAdapter> adapters = new ConcurrentHashMap<>();
    public final Agent me;
    public final DelegateStream.IO oe;
    public final PrintStream out;
    public final PrintStream err;
    public final Command.Manager cmd;
    public ServerProcess attached;
    @Autowired
    public Event.Bus<Object> eventBus;
    @Lazy
    @Autowired
    private ServerRepo servers;
    @Lazy
    @Autowired
    private AgentRunner agentRunner;

    public AgentRunner(@Autowired Agent me) {
        this.me = me;
        this.oe = new DelegateStream.IO(DelegateStream.Capability.Output, DelegateStream.Capability.Error);
        this.out = oe.output().require(o -> new PrintStream(o, true));
        this.err = oe.error().require(e -> new PrintStream(e, true));
        this.cmd = new Command.Manager(this);
    }

    @Command(usage = "")
    public String list() {
        return bean(AgentRunner.class)
                .streamServerStatusMsgs()
                .collect(Collectors.joining("\n\t- ", "Servers:\n\t- ", ""));
    }

    @Command(usage = "<name>")
    public String backup(String[] args) {
        var srv = getServer(args);
        var proc = process(srv);
        if (proc.runBackup())
            return "Backup started";
        return "Could not start backup";
    }

    @SneakyThrows
    @Command(usage = "<name> [-r]")
    public String update(String[] args) {
        var srv = getServer(args);
        var flags = args.length > 1 ? args[1] : "";
        var proc = process(srv);
        return proc.runUpdate(flags) ? srv + " already up to date" : srv + " updated";
    }

    @Command(usage = "<name>")
    public Object status(String[] args) {
        var srv = getServer(args);
        return agentRunner.process(srv);
    }

    @Command(usage = "<name> [-na]")
    public Object enable(String[] args, ConsoleController.Connection con) {
        var srv = getServer(args);
        var flags = args.length > 1 ? args[1] : "";
        servers.setEnabled(srv.getId(), true);
        if (flags.contains("n"))
            return start(args, con);
        return srv + " is now enabled";
    }

    @Command(usage = "<name> [-nt]")
    public Object disable(String[] args, ConsoleController.Connection con) {
        var srv = getServer(args);
        var flags = args.length > 1 ? args[1] : "";
        servers.setEnabled(srv.getId(), false);
        if (flags.contains("n"))
            return stop(args);
        return srv + " is now disabled";
    }

    @Command(usage = "<name> [-a]")
    public Object start(String[] args, ConsoleController.Connection con) {
        var srv = getServer(args);
        var flags = args.length > 1 ? args[1] : "";
        agentRunner.process(srv).start();
        if (flags.contains("a"))
            attach(args, con);
        return srv + " was started";
    }

    @Command(usage = "<name> [time]")
    public Object stop(String[] args) {
        var srv = getServer(args);
        var timeout = args.length > 2 ? Integer.parseInt(args[2]) : 10;
        agentRunner.process(srv).shutdown("Admin shutdown", timeout)
                .thenRun(() -> out.println(srv + " was shut down"));
        return srv + " will shut down in " + timeout + " seconds";
    }

    @Command(usage = "<name> [y/n]")
    public Object maintenance(String[] args, ConsoleController.Connection con) {
        var srv = getServer(args);
        var proc = process(srv);
        var val = args.length > 1 ? StandardValueType.BOOLEAN.parse(args[2]) : !proc.getServer().isMaintenance();
        return srv + (proc.pushMaintenance(val) ? " was " : " could not be ")
                + (val ? " put into " : " taken out of ") + " Maintenance mode";
    }

    @Command(usage = "<name> <command...>")
    public String execute(String[] args) {
        var srv = getServer(args);
        var proc = agentRunner.process(srv);

        if (proc.getState() != ServerProcess.State.Running)
            throw new Command.MildError("Server is not running");

        // forward command
        var in = proc.getIn();
        in.println(Arrays.stream(args).skip(1).collect(Collectors.joining(" ")));
        return "Executing command";
    }

    @Command(usage = "<name>")
    public String attach(String[] args, ConsoleController.Connection con) {
        var srv = getServer(args);
        var proc = agentRunner.process(srv);

        if (attached != null)
            con.detach();

        if (proc.getState() != ServerProcess.State.Running)
            throw new Command.MildError("Server is not running");
        con.attach(proc);
        attached = proc;
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

    @Command(usage = "")
    public String shutdown() {
        System.exit(0);
        return "shutting down";
    }

    private Server getServer(String[] args) {
        var srv = servers.findByAgentAndName(agentRunner.getMe().getId(), args[0]).orElse(null);
        if (srv == null) throw new Command.ArgumentError("name", "Server not found");
        return srv;
    }

    @Override
    public void handleResponse(String text) {
        out.println(text);
    }

    public Stream<Server> streamServers() {
        return Polyfill.stream(bean(ServerRepo.class).findAllForAgent(getMe().getId()));
    }

    public Stream<String> streamServerStatusMsgs() {
        return streamServers().map(this::process).map(ServerProcess::toString);
    }

    public ServerProcess process(final Server srv) {
        return processes.computeIfAbsent(srv.getId(), $ -> new ServerProcess(this, srv));
    }

    public DiscordAdapter adapter(final DiscordBot bot) {
        return adapters.computeIfAbsent(bot.getId(), $ -> new DiscordAdapter(bot));
    }

    public void execute(String cmd, ConsoleController.Connection con) {
        if (!"detach".equals(cmd) && con.getProcess() != null && attached != null)
            con.getProcess().getIn().println(cmd);
        else getCmd().execute(cmd, con);
    }

    @PreDestroy
    public void close() {
        CompletableFuture.allOf(processes.values().stream()
                        .map(proc -> proc.shutdown("Host shutdown", 5))
                        .toArray(CompletableFuture[]::new))
                .join();
    }
}
