package org.comroid.mcsd.hub;

import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.client.ClientBuilder;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.keyverifier.AcceptAllServerKeyVerifier;
import org.comroid.mcsd.core.module.ServerModule;
import org.comroid.mcsd.core.module.status.StatusModule;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ImportResource;

import java.util.List;

@Slf4j
@ImportResource({"classpath:beans.xml"})
@SpringBootApplication(exclude = DataSourceAutoConfiguration.class, scanBasePackages = "org.comroid.mcsd.*")
public class Program {
    public static void main(String[] args) {
        SpringApplication.run(Program.class, args);
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

