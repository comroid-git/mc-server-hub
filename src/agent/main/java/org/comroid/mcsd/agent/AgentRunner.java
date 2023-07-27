package org.comroid.mcsd.agent;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.annotation.PreDestroy;
import jakarta.persistence.Transient;
import lombok.Data;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.Value;
import org.comroid.api.Command;
import org.comroid.api.DelegateStream;
import org.comroid.api.Polyfill;
import org.comroid.api.UncheckedCloseable;
import org.comroid.api.io.FileHandle;
import org.comroid.mcsd.agent.controller.ConsoleController;
import org.comroid.mcsd.core.entity.Agent;
import org.comroid.mcsd.core.entity.Server;
import org.comroid.mcsd.core.repo.ServerRepo;
import org.comroid.util.Debug;
import org.comroid.util.JSON;
import org.comroid.util.MD5;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.net.URL;
import java.util.Arrays;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.comroid.mcsd.core.util.ApplicationContextProvider.bean;

@Getter
@Service
public class AgentRunner implements UncheckedCloseable, Command.Handler {
    public final Map<UUID, ServerProcess> processes = new ConcurrentHashMap<>();
    public final Agent me;
    public final @JsonIgnore DelegateStream.IO oe;
    public final @JsonIgnore PrintStream out;
    public final @JsonIgnore PrintStream err;
    public final @JsonIgnore Command.Manager cmd;
    @Lazy
    @Autowired
    private ServerRepo servers;
    @Lazy @Autowired
    private AgentRunner agentRunner;

    public AgentRunner(@Autowired Agent me) {
        this.me = me;
        this.oe = new DelegateStream.IO(DelegateStream.Capability.Output, DelegateStream.Capability.Error);
        this.out = oe.output().require(o -> new PrintStream(o,true));
        this.err = oe.error().require(e -> new PrintStream(e,true));
        this.cmd = new Command.Manager(this);

        if (Debug.isDebug())
            //oe.redirectToLogger(Log.get("Agent-"+getId()));
            oe.redirectToSystem();
    }

    @Command(usage="")
    public String list() {
        return bean(AgentRunner.class)
                .streamServerStatusMsgs()
                .collect(Collectors.joining("\n\t- ", "Servers:\n\t- ", ""));
    }

    @SneakyThrows
    @Command(usage="<name> [-r]")
    public String update(String[] args) {
        var srv = getServer(args);
        var flags = args.length>1?args[1]:"";

        // modify server.properties
        Properties prop;
        var serverProperties = new FileHandle(srv.path("server.properties").toFile());
        if (!serverProperties.exists()) {
            serverProperties.mkdirs();
            serverProperties.createNewFile();
        }
        try (var in = new FileInputStream(serverProperties)) {
            prop = srv.updateProperties(in);
        }
        try (var out = new FileOutputStream(serverProperties,false)) {
            prop.store(out, "Managed Server Properties by MCSD");
        }

        // download server.jar
        var type = switch(srv.getMode()){
            case Vanilla -> "vanilla";
            case Paper -> "servers";
            case Forge, Fabric -> "modded";
        };
        var mode = srv.getMode().name().toLowerCase();
        var version = srv.getMcVersion();
        var serverJar = new FileHandle(srv.path("server.jar").toFile());
        if (!serverJar.exists()) {
            serverJar.mkdirs();
            serverJar.createNewFile();
        } else {
            try (var source = new JSON.Deserializer(new URL("https://serverjars.com/api/fetchDetails/%s/%s/%s"
                    .formatted(type,mode,version)).openStream());
                 var local = new FileInputStream(serverJar)) {
                var sourceMd5 = source.readObject().get("response").get("md5").asString();
                var localMd5 = MD5.calculate(local);

                if (!flags.contains("r") && sourceMd5.equals(localMd5))
                    return srv + " already up to date";
            }
        }
        try (var in = new URL("https://serverjars.com/api/fetchJar/%s/%s/%s"
                .formatted(type,mode,version)).openStream();
             var out = new FileOutputStream(serverJar,false)) {
            in.transferTo(out);
        }

        // eula.txt
        var eulaTxt = new FileHandle(srv.path("eula.txt").toFile());
        if (!eulaTxt.exists()) {
            eulaTxt.mkdirs();
            eulaTxt.createNewFile();
        }
        try (var in = new DelegateStream.Input(new StringReader("eula=true\n"));
             var out = new FileOutputStream(eulaTxt,false)) {
            in.transferTo(out);
        }

        return srv + " updated";
    }

    @Command(usage="<name> <arg> [-flag]")
    public Object server(String[] args, ConsoleController.Connection con) {
        var srv = getServer(args);

        // handle arg
        var flags = args.length > 2 ? args[2] : "";
        switch (args[1]) {
            case "status":
                return agentRunner.process(srv);
            case "enable":
                servers.setEnabled(srv.getId(), true);
                if (!flags.contains("now"))
                    return srv + " is now enabled";
            case "start":
                agentRunner.process(srv).start();
                if (flags.contains("a"))
                    attach(args, con);
                return srv + " was started";
            case "disable":
                servers.setEnabled(srv.getId(), false);
                if (!flags.contains("now"))
                    return srv + " is now disabled";
            case "stop":
                agentRunner.process(srv).close();
                return srv + " was stopped";
        }
        throw new Command.ArgumentError("Invalid argument: " + args[1]);
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

    @Command(usage="<name>")
    public void attach(String[] args, ConsoleController.Connection con) {
        var srv = getServer(args);
        var proc = agentRunner.process(srv);

        if (proc.getState() != ServerProcess.State.Running)
            throw new Command.MildError("Server is not running");
        con.attach(proc);
    }

    @Command(usage="")
    public String detach(String[] args, ConsoleController.Connection con) {
        con.detach();
        return "Detached";
    }

    @Command(usage="")
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
        final var id = srv.getId();
        return processes.computeIfAbsent(id, $ -> new ServerProcess(srv));
    }

    @PreDestroy
    public void close() {
        processes.values().forEach(ServerProcess::close);
    }
}
