package org.comroid.mcsd.hub;

import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.client.ClientBuilder;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.keyverifier.AcceptAllServerKeyVerifier;
import org.comroid.mcsd.core.MinecraftServerHubConfig;
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
import java.util.logging.Level;

import static org.comroid.mcsd.core.MinecraftServerHubConfig.cronLog;

@Slf4j
@ImportResource({"classpath:beans.xml"})
@SpringBootApplication(exclude = DataSourceAutoConfiguration.class, scanBasePackages = "org.comroid.mcsd.*")
public class MinecraftServerHub {
    public static void main(String[] args) {
        SpringApplication.run(MinecraftServerHub.class, args);
    }

    @Bean
    public SshClient ssh() {
        SshClient client = ClientBuilder.builder()
                .serverKeyVerifier(AcceptAllServerKeyVerifier.INSTANCE) // todo This is bad and unsafe
                .build();
        client.start();
        return client;
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
        cronLog.log(Level.FINER, "Running Watchdog");
        /*
        StreamSupport.stream(servers.findAll().spliterator(), parallelCron)
                .filter(Server::isManaged)
                .map(Server::con)
                .map(ServerConnection::getGame)
                .filter(con -> !con.channel.isOpen())
                .peek(con -> cronLog.log(Level.WARNING, "Connection to " + con.server.con() + " is dead; restarting!"))
                .forEach(GameConnection::reconnect);
         */
        cronLog.log(Level.FINER, "Watchdog finished");
    }

    @Synchronized
    private void $cronManager() {
        cronLog.log(Level.FINER, "Running Manager");
        /*
        StreamSupport.stream(servers.findAll().spliterator(), parallelCron)
                .filter(Server::isManaged)
                .map(Server::con)
                .forEach(ServerConnection::cron);
         */
        cronLog.log(Level.FINER, "Manager finished");
    }

    @Synchronized
    private void $cronBackup() {
        cronLog.log(Level.FINER, "Running Backup Queue");
        /*
        StreamSupport.stream(servers.findAll().spliterator(), parallelCron)
                .filter(Server::isManaged)
                .filter(srv -> srv.getLastBackup().plus(srv.getBackupPeriod()).isBefore(Instant.now()))
                .filter(srv -> !srv.con().getBackupRunning().get())
                .filter(srv -> srv.con().runBackup())
                .peek(srv -> cronLog.info("Successfully created backup of " + srv))
                .forEach(servers::bumpLastBackup);
         */
        cronLog.log(Level.FINER, "Backup Queue finished");
    }

    @Synchronized
    private void $cronUpdate() {
        cronLog.log(Level.FINER, "Running Update Queue");
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
        cronLog.log(Level.FINER, "Update Queue finished");
    }
    //endregion
}

