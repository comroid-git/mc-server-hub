package org.comroid.mcsd.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mysql.jdbc.Driver;
import lombok.extern.slf4j.Slf4j;
import org.comroid.api.io.FileHandle;
import org.comroid.mcsd.web.dto.DBInfo;
import org.comroid.mcsd.web.dto.OAuth2Info;
import org.comroid.mcsd.web.entity.Server;
import org.comroid.mcsd.web.repo.ServerRepo;
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
@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
//@ComponentScan(basePackages = {"org.comroid.mcsd.web.config","org.comroid.mcsd.web.repo","org.comroid.mcsd.web.controller"})
@ImportResource("classpath:beans.xml")
public class MinecraftServerHub {
    public static final Duration CRON_MANAGE_RATE = Duration.ofMinutes(10);
    public static final Duration CRON_QUEUE_RATE = Duration.ofHours(1);
    private final Object cronLock = new Object();

    @Lazy
    @Autowired
    private ServerRepo servers;

    public static void main(String[] args) {
        SpringApplication.run(MinecraftServerHub.class, args);
    }

    @Bean
    public ScheduledFuture<?> cronManager(@Autowired TaskScheduler scheduler) {
        return scheduler.scheduleAtFixedRate(this::$cronManager, CRON_MANAGE_RATE);
    }

    @Bean
    public ScheduledFuture<?> cronBackup(@Autowired TaskScheduler scheduler) {
        return scheduler.scheduleAtFixedRate(this::$cronBackup, CRON_QUEUE_RATE);
    }

    @Bean
    public ScheduledFuture<?> cronUpdate(@Autowired TaskScheduler scheduler) {
        return scheduler.scheduleAtFixedRate(this::$cronUpdate, CRON_QUEUE_RATE);
    }

    private synchronized void $cronManager() {
        StreamSupport.stream(servers.findAll().spliterator(), true)
                .filter(Server::isManaged)
                .forEach(Server::cron);
    }

    private synchronized void $cronBackup() {
        StreamSupport.stream(servers.findAll().spliterator(), true)
                .filter(Server::isManaged)
                .filter(srv -> srv.getLastBackup().plus(srv.getBackupPeriod()).isBefore(Instant.now()))
                .filter(con -> !con.getBackupRunning().get())
                .filter(Server::runBackupScreen)
                .peek(srv -> log.info("Successfully created backup of " + srv))
                .forEach(servers::bumpLastBackup);
    }

    private synchronized void $cronUpdate() {
        StreamSupport.stream(servers.findAll().spliterator(), true)
                .filter(Server::isManaged)
                .filter(srv -> srv.getLastUpdate().plus(srv.getBackupPeriod()).isBefore(Instant.now()))
                .filter(Server::uploadRunScript)
                .filter(Server::uploadProperties)
                .filter(Server::stopServer)
                .filter(Server::runUpdate)
                .filter(Server::startServer)
                .peek(srv -> log.info("Successfully updated " + srv))
                .forEach(servers::bumpLastUpdate);
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

