package org.comroid.mcsd.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mysql.jdbc.Driver;
import lombok.extern.slf4j.Slf4j;
import org.comroid.api.io.FileHandle;
import org.comroid.mcsd.web.entity.Server;
import org.comroid.mcsd.web.model.ServerConnection;
import org.comroid.mcsd.web.repo.ServerRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ImportResource;
import org.springframework.scheduling.TaskScheduler;

import javax.sql.DataSource;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ScheduledFuture;
import java.util.function.Predicate;
import java.util.stream.StreamSupport;

@Slf4j
@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
//@ComponentScan(basePackages = {"org.comroid.mcsd.web.config","org.comroid.mcsd.web.repo","org.comroid.mcsd.web.controller"})
@ImportResource("classpath:beans.xml")
public class MinecraftServerHub {
    public static final FileHandle PATH_BASE = new FileHandle("/srv/mcsd/", true); // server path base
    public static final FileHandle OAUTH_FILE = PATH_BASE.createSubFile("oauth2.json");
    public static final FileHandle DB_FILE = PATH_BASE.createSubFile("db.json");
    public static final Duration CRON_MANAGE_RATE = Duration.ofMinutes(10);
    public static final Duration CRON_BACKUP_RATE = Duration.ofMinutes(30);

    public static void main(String[] args) {
        SpringApplication.run(MinecraftServerHub.class, args);
    }

    @Bean
    public ScheduledFuture<?> cronManager(@Autowired TaskScheduler scheduler, final @Autowired ServerRepo servers) {
        return scheduler.scheduleAtFixedRate(() -> StreamSupport.stream(servers.findAll().spliterator(), true)
                .filter(Server::isManaged)
                .map(Server::getConnection)
                .forEach(ServerConnection::cron), CRON_MANAGE_RATE);
    }

    @Bean
    public ScheduledFuture<?> cronBackup(@Autowired TaskScheduler scheduler, final @Autowired ServerRepo servers) {
        return scheduler.scheduleAtFixedRate(() -> StreamSupport.stream(servers.findAll().spliterator(), true)
                .filter(Server::isManaged)
                .filter(srv -> srv.getLastBackup().plus(srv.getBackupPeriod()).isBefore(Instant.now()))
                .map(Server::getConnection)
                .filter(con -> !con.getBackupRunning().get())
                .filter(ServerConnection::runBackupRCon)
                .map(ServerConnection::getServer)
                .forEach(servers::bumpLastBackup), CRON_BACKUP_RATE);
    }

    @Bean
    public DataSource dataSource(@Autowired ObjectMapper objectMapper) throws IOException {
        var dbInfo = objectMapper.readValue(DB_FILE.openReader(), DBInfo.class);
        return DataSourceBuilder.create()
                .driverClassName(Driver.class.getCanonicalName())
                .url(dbInfo.url)
                .username(dbInfo.username)
                .password(dbInfo.password)
                .build();
    }

    private static class DBInfo {
        public String url;
        public String username;
        public String password;
    }
}

