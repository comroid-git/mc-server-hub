package org.comroid.mcsd.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;
import org.comroid.api.Command;
import org.comroid.api.DelegateStream;
import org.comroid.api.Event;
import org.comroid.api.Polyfill;
import org.comroid.api.io.FileHandle;
import org.comroid.api.os.OS;
import org.comroid.mcsd.agent.config.WebSocketConfig;
import org.comroid.mcsd.agent.controller.ApiController;
import org.comroid.mcsd.agent.controller.ConsoleController;
import org.comroid.mcsd.connector.HubConnector;
import org.comroid.mcsd.connector.gateway.GatewayClient;
import org.comroid.mcsd.connector.gateway.GatewayConnectionInfo;
import org.comroid.mcsd.connector.gateway.GatewayPacket;
import org.comroid.mcsd.core.MinecraftServerHubConfig;
import org.comroid.mcsd.core.entity.Agent;
import org.comroid.mcsd.core.entity.Server;
import org.comroid.mcsd.core.exception.EntityNotFoundException;
import org.comroid.mcsd.core.repo.AgentRepo;
import org.comroid.mcsd.core.repo.ServerRepo;
import org.comroid.util.Debug;
import org.comroid.util.JSON;
import org.comroid.util.MD5;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ImportResource;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.TaskScheduler;
import org.yaml.snakeyaml.reader.StreamReader;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.StringReader;
import java.net.URL;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Level;
import java.util.stream.Collectors;

import static org.comroid.mcsd.core.MinecraftServerHubConfig.cronLog;
import static org.comroid.mcsd.core.util.ApplicationContextProvider.bean;

@Slf4j
@ImportResource({"classpath:beans.xml"})
@SpringBootApplication(scanBasePackages = "org.comroid.mcsd.*")
@ComponentScan(basePackageClasses = {ApiController.class, WebSocketConfig.class})
public class MinecraftServerHubAgent {
    @Lazy @Autowired
    private ServerRepo servers;
    @Lazy @Autowired
    private AgentRunner runner;

    public static void main(String[] args) {
        if (!Debug.isDebug() && !OS.isUnix)
            throw new RuntimeException("Only Unix operation systems are supported");
        SpringApplication.run(MinecraftServerHubAgent.class, args);
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
                return runner.process(srv);
            case "enable":
                servers.setEnabled(srv.getId(), true);
                if (!flags.contains("now"))
                    return srv + " is now enabled";
            case "start":
                runner.process(srv).start();
                if (flags.contains("a"))
                    attach(args, con);
                return srv + " was started";
            case "disable":
                servers.setEnabled(srv.getId(), false);
                if (!flags.contains("now"))
                    return srv + " is now disabled";
            case "stop":
                runner.process(srv).close();
                return srv + " was stopped";
        }
        throw new Command.ArgumentError("Invalid argument: " + args[1]);
    }

    @Command(usage = "<name> <command...>")
    public String execute(String[] args) {
        var srv = getServer(args);
        var proc = runner.process(srv);

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
        var proc = runner.process(srv);

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
        var srv = servers.findByAgentAndName(runner.getMe().getId(), args[0]).orElse(null);
        if (srv == null) throw new Command.ArgumentError("name", "Server not found");
        return srv;
    }

    @Bean
    @SneakyThrows
    public GatewayConnectionInfo gatewayConnectionInfo(@Autowired ObjectMapper mapper, @Autowired FileHandle configDir) {
        return mapper.readValue(configDir.createSubFile("gateway.json"), GatewayConnectionInfo.class);
    }

    @Bean
    public AgentRunner me(@Autowired GatewayConnectionInfo connectionData, @Autowired AgentRepo agents) {
        return agents.findById(connectionData.getAgent())
                .map(me -> {
                    me.cmd.register(this);
                    return new AgentRunner(me);
                })
                .orElseThrow(() -> new EntityNotFoundException(Agent.class, connectionData.getAgent()));
    }

    //@Bean
    public HubConnector connector(@Autowired GatewayConnectionInfo connectionData, @Autowired ScheduledExecutorService scheduler) {
        return new HubConnector(connectionData, scheduler);
    }

    //@Bean
    public GatewayClient gateway(@Autowired HubConnector connector) {
        return connector.getGateway();
    }

    //@Bean
    public Event.Listener<GatewayPacket> gatewayListener(@Autowired GatewayClient gateway) {
        return gateway.register(this);
    }

    //region Cron
    @Bean
    public Map<Runnable, Duration> cronjobs() {
        return Map.of(
                this::$cronWatchdog, MinecraftServerHubConfig.CronRate_Watchdog,
                this::$cronManager, MinecraftServerHubConfig.CronRate_Manager,
                this::$cronBackup, MinecraftServerHubConfig.CronRate_Queue,
                this::$cronUpdate, MinecraftServerHubConfig.CronRate_Queue
        );
    }

    @Bean
    @Lazy(false)
    public Map<Runnable, Duration> startCronjobs(@Autowired TaskScheduler scheduler, @Autowired Map<Runnable, Duration> cronjobs) {
        cronjobs.forEach(scheduler::scheduleAtFixedRate);
        return cronjobs;
    }

    @Synchronized
    private void $cronWatchdog() {
        cronLog.log(Level.FINE, "Running Watchdog");
        Polyfill.stream(servers.findAll())
                .filter(Server::isEnabled)
                .map(runner::process)
                .filter(proc->proc.getState()!= ServerProcess.State.Running)
                .peek(proc->cronLog.warning("Enabled "+proc.getServer()+" is offline! Starting..."))
                .forEach(ServerProcess::start);
        cronLog.log(Level.FINE, "Watchdog finished");
    }

    @Synchronized
    private void $cronManager() {
        cronLog.log(Level.FINE, "Running Manager");
        /*
        StreamSupport.stream(servers.findAll().spliterator(), parallelCron)
                .filter(Server::isManaged)
                .map(Server::con)
                .forEach(ServerConnection::cron);
         */
        cronLog.log(Level.FINE, "Manager finished");
    }

    @Synchronized
    private void $cronBackup() {
        cronLog.log(Level.FINE, "Running Backup Queue");
        /*
        StreamSupport.stream(servers.findAll().spliterator(), parallelCron)
                .filter(Server::isManaged)
                .filter(srv -> srv.getLastBackup().plus(srv.getBackupPeriod()).isBefore(Instant.now()))
                .filter(srv -> !srv.con().getBackupRunning().get())
                .filter(srv -> srv.con().runBackup())
                .peek(srv -> cronLog.info("Successfully created backup of " + srv))
                .forEach(servers::bumpLastBackup);
         */
        cronLog.log(Level.FINE, "Backup Queue finished");
    }

    @Synchronized
    private void $cronUpdate() {
        cronLog.log(Level.FINE, "Running Update Queue");
        /*
        StreamSupport.stream(servers.findAll().spliterator(), parallelCron)
                .filter(Server::isManaged)
                .filter(srv -> srv.getLastUpdate().plus(srv.getBackupPeriod()).isBefore(Instant.now()))
                .map(Server::con)
                .filter(ServerConnection::uploadRunScript)
                .filter(ServerConnection::uploadProperties)
                .filter(ServerConnection::stopServer)
                .filter(ServerConnection::runUpdate)
                .filter(ServerConnection::startServer)
                .map(ServerConnection::getServer)
                .peek(srv -> cronLog.info("Successfully updated " + srv))
                .forEach(servers::bumpLastUpdate);
         */
        cronLog.log(Level.FINE, "Update Queue finished");
    }
    //endregion
}

