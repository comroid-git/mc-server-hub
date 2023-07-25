package org.comroid.mcsd.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mysql.jdbc.Driver;
import lombok.extern.slf4j.Slf4j;
import org.comroid.api.io.FileHandle;
import org.comroid.mcsd.api.dto.DBInfo;
import org.comroid.mcsd.api.dto.OAuth2Info;
import org.comroid.mcsd.core.repo.ServerRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportResource;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import javax.sql.DataSource;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Logger;

@Slf4j
@Configuration
@ImportResource({"classpath:beans.xml"})
public class MinecraftServerHubConfig {
    public static final Duration CronRate_Watchdog = Duration.ofSeconds(10);
    public static final Duration CronRate_Manager = Duration.ofMinutes(5);
    public static final Duration CronRate_Queue = Duration.ofHours(1);
    public static final Logger cronLog = Logger.getLogger("cron");

    @Lazy
    @Autowired
    private ServerRepo servers;

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

    @Bean
    public ScheduledExecutorService scheduler() {
        return Executors.newScheduledThreadPool(32);
    }
}

