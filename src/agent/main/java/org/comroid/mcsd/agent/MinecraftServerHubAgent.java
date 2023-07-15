package org.comroid.mcsd.agent;

import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;
import org.comroid.api.info.Log;
import org.comroid.mcsd.repo.ServerRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ImportResource;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.TaskScheduler;

import java.time.Duration;
import java.util.concurrent.ScheduledFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

@Slf4j
@ImportResource({"classpath:beans.xml"})
@SpringBootApplication(exclude = DataSourceAutoConfiguration.class, scanBasePackages = "org.comroid.mcsd")
public class MinecraftServerHubAgent {
    public static final Duration CRON_WATCHDOG_RATE = Duration.ofSeconds(10);
    public static final Duration CRON_MANAGE_RATE = Duration.ofMinutes(10);
    public static final Duration CRON_QUEUE_RATE = Duration.ofHours(1);
    private final Object cronLock = new Object();

    @Lazy
    @Autowired
    private ServerRepo servers;

    public static void main(String[] args) {
        SpringApplication.run(MinecraftServerHubAgent.class, args);
    }

    @Bean @Lazy(false)
    public ScheduledFuture<?> cronWatchdog(@Autowired TaskScheduler scheduler) {
        return scheduler.scheduleAtFixedRate(this::$cronWatchdog, CRON_WATCHDOG_RATE);
    }

    public ScheduledFuture<?> cronManager(@Autowired TaskScheduler scheduler) {
        return scheduler.scheduleAtFixedRate(this::$cronManager, CRON_MANAGE_RATE);
    }

    @Bean @Lazy(false)
    public ScheduledFuture<?> cronBackup(@Autowired TaskScheduler scheduler) {
        return scheduler.scheduleAtFixedRate(this::$cronBackup, CRON_QUEUE_RATE);
    }

    //@Bean
    public ScheduledFuture<?> cronUpdate(@Autowired TaskScheduler scheduler) {
        return scheduler.scheduleAtFixedRate(this::$cronUpdate, CRON_QUEUE_RATE);
    }

    private static final Logger cronLog = Log.get("cron");
    private static final boolean parallelCron = false;

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
}

