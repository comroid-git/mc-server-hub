package org.comroid.mcsd.hub;

import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.client.ClientBuilder;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.keyverifier.AcceptAllServerKeyVerifier;
import org.comroid.mcsd.core.MinecraftServerHubConfig;
import org.comroid.mcsd.core.module.ServerModule;
import org.comroid.mcsd.core.module.discord.DiscordModule;
import org.comroid.mcsd.core.module.local.LocalExecutionModule;
import org.comroid.mcsd.core.module.local.LocalFileModule;
import org.comroid.mcsd.core.module.player.ChatModule;
import org.comroid.mcsd.core.module.status.StatusModule;
import org.comroid.mcsd.core.module.status.UpdateModule;
import org.comroid.mcsd.core.module.status.UptimeModule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ImportResource;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.TaskScheduler;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

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

    @Bean
    public List<ServerModule.Factory<?>> serverModuleFactories() {
        return List.of(StatusModule.Factory);
    }
}

