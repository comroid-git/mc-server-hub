package org.comroid.mcsd.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;
import org.comroid.api.Event;
import org.comroid.api.io.FileHandle;
import org.comroid.api.os.OS;
import org.comroid.mcsd.agent.config.WebSocketConfig;
import org.comroid.mcsd.agent.controller.ApiController;
import org.comroid.mcsd.api.model.Status;
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
import org.comroid.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ImportResource;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.TaskScheduler;

import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Level;

import static org.comroid.mcsd.core.MinecraftServerHubConfig.cronLog;
import static org.comroid.mcsd.core.util.ApplicationContextProvider.bean;

@Slf4j
@ImportResource({"classpath:beans.xml"})
@SpringBootApplication(scanBasePackages = "org.comroid.mcsd.*")
@ComponentScan(basePackageClasses = {AgentRunner.class, ApiController.class, WebSocketConfig.class})
public class MinecraftServerHubAgent {
    @Lazy @Autowired
    private ServerRepo servers;
    @Lazy @Autowired
    private AgentRunner agentRunner;

    public static void main(String[] args) {
        if (!Debug.isDebug() && !OS.isUnix)
            throw new RuntimeException("Only Unix operation systems are supported");
        SpringApplication.run(MinecraftServerHubAgent.class, args);
    }

    @Bean
    @SneakyThrows
    public GatewayConnectionInfo gatewayConnectionInfo(@Autowired ObjectMapper mapper, @Autowired FileHandle configDir) {
        return mapper.readValue(configDir.createSubFile("gateway.json"), GatewayConnectionInfo.class);
    }

    @Bean
    public Agent me(@Autowired GatewayConnectionInfo connectionData, @Autowired AgentRepo agents) {
        return agents.findById(connectionData.getAgent())
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
                this::$cronUptime, MinecraftServerHubConfig.CronRate_Uptime,
                this::$cronInternal, MinecraftServerHubConfig.CronRate_Internal,
                this::$cronBackup, MinecraftServerHubConfig.CronRate_Queue,
                this::$cronUpdate, MinecraftServerHubConfig.CronRate_Queue
        );
    }

    @Bean
    @Lazy(false)
    public Map<Runnable, Duration> startCronjobs(@Autowired TaskScheduler scheduler, @Autowired Map<Runnable, Duration> cronjobs) {
        cronjobs.forEach((task, delay) -> {
            try {
                scheduler.scheduleWithFixedDelay(()->{
                    try {
                        task.run();
                    } catch (Exception e) {
                        cronLog.log(Level.SEVERE, "An error occurred during cronjob", e);
                    }
                }, Instant.now().plus(delay), delay);
            } catch (Throwable t) {
                cronLog.log(Level.SEVERE, "Cronjob %s failed".formatted(StackTraceUtils.lessSimpleName(task.getClass())), t);
            }
        });
        return cronjobs;
    }

    @Synchronized
    private void $cronWatchdog() {
        cronLog.log(Level.FINEST, "Running Watchdog");
        agentRunner.streamServers()
                .filter(Server::isEnabled)
                .map(agentRunner::process)
                .filter(proc->proc.getState()!= ServerProcess.State.Running)
                .peek(proc->cronLog.warning("Enabled "+proc.getServer()+" is offline! Starting..."))
                .peek(proc->{
                    if (proc.isJarUpToDate()) return;
                    cronLog.warning(proc.getServer()+" is outdated; updating...");
                    proc.runUpdate();
                })
                .forEach(ServerProcess::start);
        cronLog.log(Level.FINER, "Watchdog finished");
    }

    @Synchronized
    private void $cronUptime() {
        cronLog.log(Level.FINEST, "Running Uptime job");
        agentRunner.streamServers()
                .filter(Server::isEnabled)
                .map(agentRunner::process)
                .forEach(ServerProcess::pushUptime);
        cronLog.log(Level.FINER, "Uptime job finished");
    }
  
    @Synchronized
    private void $cronInternal() {
        cronLog.log(Level.FINEST, "Running Internal jobs");
        agentRunner.streamServers()
                .filter(Server::isEnabled)
                .map(agentRunner::process)
                .forEach(ServerProcess::runTicker);
        cronLog.log(Level.FINER, "Internal jobs finished");
    }

    @SneakyThrows
    @Synchronized
    private void $cronBackup() {
        try (var url = new URL("https://raw.githubusercontent.com/comroid-git/mc-server-hub/main/global.json").openStream();
             var json = new JSON.Deserializer(url)) {
            if (!json.readObject().get("backups").asBoolean()) {
                cronLog.info("Not running Backup job because it is globally disabled");
                return;
            }
        } catch (Throwable ignored) {}
        cronLog.log(Level.FINE, "Running Backup Queue");
        agentRunner.streamServers()
                .filter(Server::isManaged)
                .filter(srv -> srv.getLastBackup().plus(srv.getBackupPeriod()).isBefore(Instant.now()))
                .map(agentRunner::process)
                .flatMap(Streams.yield(srv -> srv.getCurrentStatus().getStatus() == Status.online,
                        srv -> cronLog.warning("Not running backup job for " + srv)))
                .peek(serverProcess -> serverProcess.runBackup(false).join())
                .peek(srv -> cronLog.info("Successfully created backup of " + srv))
                .map(ServerProcess::getServer)
                .forEach(servers::bumpLastBackup);
        cronLog.log(Level.INFO, "Backup Queue finished");
    }

    @SneakyThrows
    @Synchronized
    private void $cronUpdate() {
        try (var url = new URL("https://raw.githubusercontent.com/comroid-git/mc-server-hub/main/global.json").openStream();
             var json = new JSON.Deserializer(url)) {
            if (!json.readObject().get("updates").asBoolean()) {
                cronLog.info("Not running Update job because it is globally disabled");
                return;
            }
        } catch (Throwable ignored) {}
        cronLog.log(Level.FINE, "Running Update Queue");
        agentRunner.streamServers()
                .filter(Server::isManaged)
                .filter(srv -> srv.getLastUpdate().plus(srv.getBackupPeriod()).isBefore(Instant.now()))
                .map(agentRunner::process)
                .flatMap(Streams.yield(srv -> srv.getCurrentStatus().getStatus() == Status.online,
                        srv -> cronLog.warning("Not running backup job for " + srv)))
                .filter(ServerProcess::startUpdate)
                .peek(serverProcess -> serverProcess.shutdown("Server Update", 60).join())
                .peek(proc-> {
                    assert proc.getProcess() != null;
                    proc.getProcess().onExit().join();
                })
                .filter(ServerProcess::runUpdate)
                .peek(ServerProcess::start)
                .map(ServerProcess::getServer)
                .peek(srv -> cronLog.info("Successfully updated " + srv))
                .forEach(servers::bumpLastUpdate);
        cronLog.log(Level.INFO, "Update Queue finished");
    }
    //endregion
}

