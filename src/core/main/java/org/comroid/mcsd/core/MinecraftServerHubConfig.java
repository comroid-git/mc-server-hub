package org.comroid.mcsd.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mysql.jdbc.Driver;
import lombok.extern.slf4j.Slf4j;
import org.comroid.api.DelegateStream;
import org.comroid.api.io.FileHandle;
import org.comroid.mcsd.api.dto.DBInfo;
import org.comroid.mcsd.api.dto.OAuth2Info;
import org.comroid.util.Debug;
import org.comroid.util.REST;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.*;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import javax.sql.DataSource;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Configuration
@ImportResource({"classpath:baseBeans.xml"})
@EntityScan(basePackages = "org.comroid.mcsd.core.entity")
@EnableJpaRepositories(basePackages = "org.comroid.mcsd.core.repo")
public class MinecraftServerHubConfig {
    public static final String BaseUrl = Debug.isDebug() ? "http://localhost:42064" : "https://mc.comroid.org/api";

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

    @Bean
    @Lazy(false)
    public ScheduledFuture<?> shutdownForAutoUpdateTask(@Autowired ScheduledExecutorService scheduler) {
        return scheduler.scheduleAtFixedRate(()->{
            try {
                var info = REST.get("https://api.github.com/repos/comroid-git/mc-server-hub/commits/main?per_page=1")
                        .join().getBody();
                var recent = info.get("sha").asString();
                var current = DelegateStream.readAll(ClassLoader.getSystemResourceAsStream("commit.txt"));
                if (!current.equals(recent)) {
                    log.info("Shutting down for auto update");
                    System.exit(0);
                }
            } catch (Throwable t) {
                log.error("Unable to fetch latest commit", t);
            }
        }, 72, 72, TimeUnit.HOURS);
    }
}

