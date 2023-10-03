package org.comroid.mcsd.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.common.aliasing.qual.Unique;
import org.comroid.api.Polyfill;
import org.comroid.api.info.Log;
import org.comroid.api.io.FileHandle;
import org.comroid.api.os.OS;
import org.comroid.mcsd.agent.config.WebSocketConfig;
import org.comroid.mcsd.agent.controller.ApiController;
import org.comroid.mcsd.api.dto.AgentInfo;
import org.comroid.mcsd.api.dto.McsdConfig;
import org.comroid.mcsd.core.Config;
import org.comroid.mcsd.core.entity.Agent;
import org.comroid.mcsd.core.entity.Server;
import org.comroid.mcsd.core.exception.EntityNotFoundException;
import org.comroid.mcsd.core.module.*;
import org.comroid.mcsd.core.module.discord.DiscordModule;
import org.comroid.mcsd.core.module.player.ConsolePlayerEventModule;
import org.comroid.mcsd.core.module.local.LocalExecutionModule;
import org.comroid.mcsd.core.module.local.LocalFileModule;
import org.comroid.mcsd.core.module.player.PlayerListModule;
import org.comroid.mcsd.core.module.status.UpdateModule;
import org.comroid.mcsd.core.module.status.StatusModule;
import org.comroid.mcsd.core.module.status.UptimeModule;
import org.comroid.mcsd.core.repo.AgentRepo;
import org.comroid.mcsd.core.repo.ServerRepo;
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
import java.util.Optional;
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
    public AgentInfo agentInfo(@Autowired McsdConfig config) {
        return config.getAgent();
    }

    @Bean
    public Agent me(@Autowired AgentInfo agentInfo, @Autowired AgentRepo agents) {
        return agents.findById(agentInfo.getAgent())
                .orElseThrow(() -> new EntityNotFoundException(Agent.class, agentInfo.getAgent()));
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
                ConsolePlayerEventModule.Factory,
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
        var info = bean(AgentInfo.class, "agentInfo");
        REST.request(REST.Method.GET, info.getHubBaseUrl() + "/api/open/agent/hello/"
                        + bean(Agent.class, "me").getId()
                        + (Optional.ofNullable(info.getBaseUrl())
                        .or(() -> wrap(OS.Host.class, "hostname")
                                .map(OS.Host::name)
                                .map(Config::wrapHostname))
                        .map(baseUrl -> "?baseUrl=" + baseUrl)
                        .orElse("")))
                .addHeader("Authorization", info.getToken())
                .execute()
                .thenAccept(response -> response.require(HttpStatus.NO_CONTENT.value()))
                .exceptionally(Polyfill.exceptionLogger(Log.get(), "Could not connect to Hub"));
    }
}

