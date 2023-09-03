package org.comroid.mcsd.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;
import org.comroid.api.Event;
import org.comroid.api.io.FileHandle;
import org.comroid.api.os.OS;
import org.comroid.mcsd.agent.config.WebSocketConfig;
import org.comroid.mcsd.agent.controller.ApiController;
import org.comroid.mcsd.api.model.Status;
import org.comroid.mcsd.connector.HubConnector;
import org.comroid.mcsd.connector.gateway.GatewayClient;
import org.comroid.mcsd.connector.gateway.GatewayConnectionInfo;
import org.comroid.mcsd.connector.gateway.GatewayPacket;
import org.comroid.mcsd.core.MinecraftServerHubConfig;
import org.comroid.mcsd.core.entity.Agent;
import org.comroid.mcsd.core.entity.Server;
import org.comroid.mcsd.core.entity.User;
import org.comroid.mcsd.core.exception.EntityNotFoundException;
import org.comroid.mcsd.core.module.*;
import org.comroid.mcsd.core.module.discord.DiscordModule;
import org.comroid.mcsd.core.repo.AgentRepo;
import org.comroid.mcsd.core.repo.ServerRepo;
import org.comroid.mcsd.core.repo.UserRepo;
import org.comroid.mcsd.core.util.ApplicationContextProvider;
import org.comroid.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ImportResource;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.TaskScheduler;

import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Level;

import static org.comroid.mcsd.core.util.ApplicationContextProvider.bean;

@Slf4j
@ImportResource({"classpath:beans.xml"})
@SpringBootApplication(scanBasePackages = "org.comroid.mcsd.*")
@ComponentScan(basePackageClasses = {AgentRunner.class, ApiController.class, WebSocketConfig.class})
public class MinecraftServerHubAgent {
    @Lazy @Autowired
    private ServerRepo servers;
    @Lazy @Autowired
    private AgentRunner agentRunner;
    @Lazy @Autowired
    private UserRepo users;

    public static void main(String[] args) {
        if (!Debug.isDebug() && !OS.isUnix)
            throw new RuntimeException("Only Unix operation systems are supported");
        SpringApplication.run(MinecraftServerHubAgent.class, args);
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
                FileModule.Factory,
                UpdateModule.Factory,
                ExecutionModule.Factory,
                BackupModule.Factory,
                ChatModule.Factory,
                DiscordModule.Factory
        );
    }

    //@Bean
    public HubConnector connector(@Autowired GatewayConnectionInfo connectionData, @Autowired ScheduledExecutorService scheduler) {
        return new HubConnector(connectionData, scheduler);
    }

    //@Bean
    public GatewayClient gateway(@Autowired HubConnector connector) {
        return connector.getGateway();
    }

    //@Bean
    public Event.Listener<GatewayPacket> gatewayListener(@Autowired GatewayClient gateway) {
        return gateway.register(this);
    }
}

