package org.comroid.mcsd.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.SneakyThrows;
import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;
import org.comroid.api.Event;
import org.comroid.api.io.FileHandle;
import org.comroid.api.os.OS;
import org.comroid.mcsd.connector.HubConnector;
import org.comroid.mcsd.connector.gateway.GatewayClient;
import org.comroid.mcsd.connector.gateway.GatewayConnectionInfo;
import org.comroid.mcsd.connector.gateway.GatewayPacket;
import org.comroid.mcsd.core.MinecraftServerHubConfig;
import org.comroid.mcsd.core.entity.Agent;
import org.comroid.mcsd.core.repo.AgentRepo;
import org.comroid.mcsd.core.repo.ServerRepo;
import org.comroid.util.Debug;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ImportResource;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.TaskScheduler;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Level;

import static org.comroid.mcsd.core.MinecraftServerHubConfig.cronLog;

@Slf4j
@ImportResource({"classpath:beans.xml"})
@SpringBootApplication(exclude = DataSourceAutoConfiguration.class, scanBasePackages = "org.comroid.mcsd.*")
public class MinecraftServerHubAgent {
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
        return agents.findById(connectionData.getAgent()).orElseThrow();
    }
    @Bean
    public HubConnector connector(@Autowired GatewayConnectionInfo connectionData, @Autowired ScheduledExecutorService scheduler) {
        return new HubConnector(connectionData, scheduler);
    }
    @Bean
    public GatewayClient gateway(@Autowired HubConnector connector) {
        return connector.getGateway();
    }
    @Bean
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

    @Bean @Lazy(false)
    public Map<Runnable, Duration> startCronjobs(@Autowired TaskScheduler scheduler, @Autowired Map<Runnable, Duration> cronjobs) {
        cronjobs.forEach(scheduler::scheduleAtFixedRate);
        return cronjobs;
    }

    @Synchronized
    private void $cronWatchdog() {
        cronLog.log(Level.FINE, "Running Watchdog");
        /*
        StreamSupport.stream(servers.findAll().spliterator(), parallelCron)
                .filter(Server::isManaged)
                .map(Server::con)
                .map(ServerConnection::getGame)
                .filter(con -> !con.channel.isOpen())
                .peek(con -> cronLog.log(Level.WARNING, "Connection to " + con.server.con() + " is dead; restarting!"))
                .forEach(GameConnection::reconnect);
         */
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

