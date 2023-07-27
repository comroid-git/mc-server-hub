package org.comroid.mcsd.agent.controller;

import jakarta.servlet.http.HttpSession;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.comroid.api.DelegateStream;
import org.comroid.api.Event;
import org.comroid.mcsd.agent.AgentRunner;
import org.comroid.mcsd.agent.ServerProcess;
import org.comroid.mcsd.agent.config.WebSocketConfig;
import org.comroid.mcsd.core.entity.Agent;
import org.comroid.mcsd.core.entity.User;
import org.comroid.mcsd.core.exception.EntityNotFoundException;
import org.comroid.mcsd.core.model.ServerConnection;
import org.comroid.mcsd.core.repo.UserRepo;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;

@Slf4j
@Controller
public class ConsoleController {
    private final Map<UUID, Connection> connections = new ConcurrentHashMap<>();
    @Autowired
    private SimpMessagingTemplate respond;
    @Autowired
    private UserRepo userRepo;
    @Autowired
    private AgentRunner runner;
    @Autowired
    private ScheduledExecutorService scheduler;

    @MessageMapping("/console/connect")
    public void connect(@Header("simpSessionAttributes") Map<String, Object> attr) {
        var session = (HttpSession) attr.get(WebSocketConfig.HTTP_SESSION_KEY);
        var user = userRepo.findBySession(session);
        Connection connection = new Connection(user);
        connections.put(user.getId(), connection);
        respond.convertAndSendToUser(user.getName(), "/console/handshake", "");
    }

    @MessageMapping("/console/input")
    public void input(@Header("simpSessionAttributes") Map<String, Object> sessionAttributes, @Payload String input) {
        var session = (HttpSession) sessionAttributes.get(WebSocketConfig.HTTP_SESSION_KEY);
        var user = userRepo.findBySession(session);
        var res = connections.getOrDefault(user.getId(), null);
        if (res == null)
            throw new EntityNotFoundException(Agent.class, "User " + user.getId());
        runner.me.cmd.execute(input.substring(2, input.length() - 1), res);
    }

    @MessageMapping("/console/disconnect")
    public void disconnect(@Header("simpSessionAttributes") Map<String, Object> sessionAttributes) {
        var session = (HttpSession) sessionAttributes.get(WebSocketConfig.HTTP_SESSION_KEY);
        var user = userRepo.findBySession(session);
        var res = connections.getOrDefault(user.getId(), null);
        if (res != null)
            res.close();
    }

    @Getter
    public class Connection extends Event.Bus<String> {
        private final User user;
        private @Nullable ServerProcess process;
        private CompletableFuture<?> outFwd, errFwd;

        private Connection(User user) {
            this.user = user;

            runner.me.oe.redirectToEventBus(this);
        }

        public void attach(ServerProcess process) {
            this.process = process;
            outFwd = CompletableFuture.supplyAsync(this::outputForwarder, scheduler);
            errFwd = CompletableFuture.supplyAsync(this::errorForwarder, scheduler);
        }

        public void detach() {
            if (process == null)
                return;
            outFwd.cancel(true);
            errFwd.cancel(true);
            process = null;
        }

        @SneakyThrows
        private Void outputForwarder() {
            while (process!=null){
                process.getOut().transferTo(runner.me.out);
            }
            return null;
        }

        @SneakyThrows
        private Void errorForwarder() {
            while (process!=null){
                process.getErr().transferTo(runner.me.err);
            }
            return null;
        }

        @Event.Subscriber(DelegateStream.IO.EventKey_Output)
        public void handleStdout(Event<String> e) {
            respond.convertAndSendToUser(user.getName(), "/console/output",
                    e.getData().replaceAll("\r?\n",ServerConnection.br));
        }

        @Event.Subscriber(DelegateStream.IO.EventKey_Error)
        public void handleStderr(Event<String> e) {
            respond.convertAndSendToUser(user.getName(), "/console/error",
                    e.getData().replaceAll("\r?\n",ServerConnection.br));
        }

        @Override
        @SneakyThrows
        public void closeSelf() {
            respond.convertAndSendToUser(user.getName(), "/console/disconnect", "");
            super.closeSelf();
        }
    }
}
