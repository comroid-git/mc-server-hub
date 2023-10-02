package org.comroid.mcsd.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.common.aliasing.qual.Unique;
import org.comroid.api.Polyfill;
import org.comroid.api.io.FileHandle;
import org.comroid.api.os.OS;
import org.comroid.mcsd.agent.config.WebSocketConfig;
import org.comroid.mcsd.agent.controller.ApiController;
import org.comroid.mcsd.connector.gateway.GatewayConnectionInfo;
import org.comroid.mcsd.core.Config;
import org.comroid.mcsd.core.entity.Agent;
import org.comroid.mcsd.core.entity.Server;
import org.comroid.mcsd.core.exception.EntityNotFoundException;
import org.comroid.mcsd.core.module.*;
import org.comroid.mcsd.core.module.discord.DiscordModule;
import org.comroid.mcsd.core.module.player.ChatModule;
import org.comroid.mcsd.core.module.local.LocalExecutionModule;
import org.comroid.mcsd.core.module.local.LocalFileModule;
import org.comroid.mcsd.core.module.player.PlayerListModule;
import org.comroid.mcsd.core.module.status.UpdateModule;
import org.comroid.mcsd.core.module.status.StatusModule;
import org.comroid.mcsd.core.module.status.UptimeModule;
import org.comroid.mcsd.core.repo.AgentRepo;
import org.comroid.mcsd.core.repo.ServerRepo;
import org.comroid.mcsd.core.util.ApplicationContextProvider;
import org.comroid.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ImportResource;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;

import static org.comroid.mcsd.core.util.ApplicationContextProvider.bean;
import static org.comroid.mcsd.core.util.ApplicationContextProvider.wrap;

@Slf4j
@ImportResource({"classpath:beans.xml"})
@SpringBootApplication(scanBasePackages = "org.comroid.mcsd.*")
@ComponentScan(basePackageClasses = {AgentRunner.class, ApiController.class, WebSocketConfig.class})
public class Program implements ApplicationRunner {
    public static void main(String[] args) {
        if (!Debug.isDebug() && !OS.isUnix)
            throw new RuntimeException("Only Unix operation systems are supported");
        SpringApplication.run(Program.class, args);
    }

    @Bean
    @SneakyThrows
    public GatewayConnectionInfo gatewayConnectionInfo(@Autowired ObjectMapper mapper, @Autowired FileHandle configDir) {
        return mapper.readValue(configDir.createSubFile("gateway.json"), GatewayConnectionInfo.class);
    }

    @Bean
    public Agent me(@Autowired GatewayConnectionInfo connectionData, @Autowired AgentRepo agents) {
        return agents.findById(connectionData.getAgent())
                .orElseThrow(() -> new EntityNotFoundException(Agent.class, connectionData.getAgent()));
    }

    @Bean
    public List<ServerModule.Factory<?>> serverModuleFactories() {
        return List.of(
                StatusModule.Factory,
                LocalFileModule.Factory,
                UptimeModule.Factory,
                UpdateModule.Factory,
                LocalExecutionModule.Factory,
                //todo: fix BackupModule.Factory,
                ChatModule.Factory,
                PlayerListModule.Factory,
                DiscordModule.Factory
        );
    }

    @Bean
    @Unique
    @Lazy(false)
    public List<Server> servers(@Autowired ServerRepo servers, @Autowired Agent me) {
        return Streams.of(servers.findAllForAgent(me.getId())).toList();
    }

    @Override
    public void run(ApplicationArguments args) {
        ((List<Server>) bean(List.class, "servers"))
                .forEach(srv -> {
                    srv.addChildren(((List<ServerModule.Factory<?>>) bean(List.class, "serverModuleFactories"))
                            .stream()
                            .map(factory -> factory.create(srv))
                            .toArray());
                    srv.execute(Executors.newScheduledThreadPool(4), Duration.ofSeconds(30));
                });
        REST.request(REST.Method.GET, Config.BaseUrl + "/open/agent/hello/"
                        + bean(Agent.class, "me").getId()
                        + (wrap(OS.Host.class, "hostname")
                        .map(host -> "?hostname=" + host.name())
                        .orElse("")))
                .addHeader("Authorization", "token") // todo
                .execute()
                .thenAccept(response -> response.require(HttpStatus.NO_CONTENT.value()))
                .exceptionally(Polyfill.exceptionLogger());
    }
}

