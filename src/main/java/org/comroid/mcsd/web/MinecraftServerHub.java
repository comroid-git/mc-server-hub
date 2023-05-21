package org.comroid.mcsd.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mysql.jdbc.Driver;
import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;
import org.comroid.api.info.Log;
import org.comroid.api.io.FileHandle;
import org.comroid.mcsd.web.dto.DBInfo;
import org.comroid.mcsd.web.dto.OAuth2Info;
import org.comroid.mcsd.web.entity.Server;
import org.comroid.mcsd.web.model.GameConnection;
import org.comroid.mcsd.web.model.ServerConnection;
import org.comroid.mcsd.web.repo.ServerRepo;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ImportResource;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.TaskScheduler;

import javax.sql.DataSource;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ScheduledFuture;
import java.util.stream.StreamSupport;

@Slf4j
@ImportResource({"classpath:beans.xml"})
@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
public class MinecraftServerHub {
    public static final Duration CRON_WATCHDOG_RATE = Duration.ofSeconds(10);
    public static final Duration CRON_MANAGE_RATE = Duration.ofMinutes(10);
    public static final Duration CRON_QUEUE_RATE = Duration.ofHours(1);
    private final Object cronLock = new Object();

    @Lazy
    @Autowired
    private ServerRepo servers;

    public static void main(String[] args) {
        SpringApplication.run(MinecraftServerHub.class, args);
    }

    @Bean @Lazy(false)
    public ScheduledFuture<?> cronWatchdog(@Autowired TaskScheduler scheduler) {
        return scheduler.scheduleAtFixedRate(this::$cronWatchdog, CRON_WATCHDOG_RATE);
    }

    @Bean @Lazy(false)
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
        cronLog.debug("Running Watchdog");
        StreamSupport.stream(servers.findAll().spliterator(), parallelCron)
                .filter(Server::isManaged)
                .map(Server::con)
                .map(ServerConnection::getGame)
                .filter(con -> !con.channel.isConnected())
                .peek(con -> cronLog.warn("Connection to " + con.server.con() + " is dead; restarting!"))
                .forEach(GameConnection::reconnect);
        cronLog.debug("Watchdog finished");
    }

    @Synchronized
    private void $cronManager() {
        cronLog.debug("Running Manager");
        StreamSupport.stream(servers.findAll().spliterator(), parallelCron)
                .filter(Server::isManaged)
                .map(Server::con)
                .forEach(ServerConnection::cron);
        cronLog.debug("Manager finished");
    }

    @Synchronized
    private void $cronBackup() {
        cronLog.debug("Running Backup Queue");
        StreamSupport.stream(servers.findAll().spliterator(), parallelCron)
                .filter(Server::isManaged)
                .filter(srv -> srv.getLastBackup().plus(srv.getBackupPeriod()).isBefore(Instant.now()))
                .filter(srv -> !srv.con().getBackupRunning().get())
                .filter(srv -> srv.con().runBackup())
                .peek(srv -> cronLog.info("Successfully created backup of " + srv))
                .forEach(servers::bumpLastBackup);
        cronLog.debug("Backup Queue finished");
    }

    @Synchronized
    private void $cronUpdate() {
        cronLog.debug("Running Update Queue");
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
        cronLog.debug("Update Queue finished");
    }

    @Bean(name = "configDir")
    @Order(Ordered.HIGHEST_PRECEDENCE)
    @ConditionalOnExpression(value = "environment.containsProperty('DEBUG')")
    public FileHandle configDir_Debug() {
        log.info("Using debug configuration directory");
        return new FileHandle("/srv/mcsd-dev/", true);
    }

    @Bean
    @Order
    @ConditionalOnMissingBean(name = "configDir")
    public FileHandle configDir() {
        log.info("Using production configuration directory");
        return new FileHandle("/srv/mcsd/", true);
    }

    @Bean
    public DBInfo dataSourceInfo(@Autowired ObjectMapper objectMapper, @Autowired FileHandle configDir) throws IOException {
        return objectMapper.readValue(configDir.createSubFile("db.json"), DBInfo.class);
    }

    @Bean
    public OAuth2Info oAuthInfo(@Autowired ObjectMapper objectMapper, @Autowired FileHandle configDir) throws IOException {
        return objectMapper.readValue(configDir.createSubFile("oauth2.json"), OAuth2Info.class);
    }

    @Bean
    public DataSource dataSource(@Autowired DBInfo dbInfo) {
        return DataSourceBuilder.create()
                .driverClassName(Driver.class.getCanonicalName())
                .url(dbInfo.getUrl())
                .username(dbInfo.getUsername())
                .password(dbInfo.getPassword())
                .build();
    }

}

