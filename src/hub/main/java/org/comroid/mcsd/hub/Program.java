package org.comroid.mcsd.hub;

import lombok.extern.slf4j.Slf4j;
import org.comroid.mcsd.core.module.ServerModule;
import org.comroid.mcsd.core.module.status.StatusModule;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ImportResource;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.List;

@Slf4j
//@EnableWebMvc
@ImportResource({"classpath:beans.xml"})
@SpringBootApplication(exclude = DataSourceAutoConfiguration.class, scanBasePackages = "org.comroid.mcsd.*")
public class Program {
    public static void main(String[] args) {
        SpringApplication.run(Program.class, args);
    }
}

