package org.comroid.mcsd.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mysql.jdbc.Driver;
import org.comroid.api.io.FileHandle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ImportResource;

import javax.sql.DataSource;
import java.io.IOException;
import java.util.stream.Stream;

@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
//@ComponentScan(basePackages = {"org.comroid.mcsd.web.config","org.comroid.mcsd.web.repo","org.comroid.mcsd.web.controller"})
@ImportResource("classpath:beans.xml")
public class MinecraftServerHub {
    public static final FileHandle PATH_BASE = new FileHandle("/srv/mcsd/", true); // server path base
    public static final FileHandle OAUTH_FILE = PATH_BASE.createSubFile("oauth2.json");
    public static final FileHandle DB_FILE = PATH_BASE.createSubFile("db.json");

    public static void main(String[] args) {
        SpringApplication.run(MinecraftServerHub.class, args);
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

