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
import org.comroid.mcsd.core.entity.User;
import org.comroid.mcsd.core.entity.UserData;
import org.comroid.mcsd.core.model.ServerConnection;
import org.comroid.mcsd.core.repo.UserDataRepo;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Controller
public class ConsoleController {
    private final Map<UUID, Connection> connections = new ConcurrentHashMap<>();
    @Autowired
    private SimpMessagingTemplate respond;
    @Autowired
    private UserDataRepo userRepo;
    @Autowired
    private AgentRunner agentRunner;

    public Connection con(Map<String, Object> attr) {
        return con(user(attr));
    }
    public Connection con(final UserData user) {
        return connections.computeIfAbsent(user.getId(), $->new Connection(user));
    }
    public UserData user(Map<String, Object> attr) {
        var session = (HttpSession) attr.get(WebSocketConfig.HTTP_SESSION_KEY);
        return userRepo.get(session);
    }

    @MessageMapping("/console/connect")
    public void connect(@Header("simpSessionAttributes") Map<String, Object> attr) {
        var user = user(attr);
        var con = con(user);
        respond.convertAndSendToUser(user.getName(), "/console/handshake", "");
    }

    @MessageMapping("/console/input")
    public void input(@Header("simpSessionAttributes") Map<String, Object> attr, @Payload String input) {
        var user = user(attr);
        var con = con(user);
        var cmd = input.substring(2, input.length() - 1);
        con.publish("stdout", "> "+cmd+'\n');
        agentRunner.execute(cmd, con);
    }

    @MessageMapping("/console/disconnect")
    public void disconnect(@Header("simpSessionAttributes") Map<String, Object> attr) {
        var user = user(attr);
        con(user).close();
    }

    @Getter
    public class Connection extends Event.Bus<String> {
        private final UserData user;
        private @Nullable ServerProcess process;

        private Connection(UserData user) {
            this.user = user;

            agentRunner.oe.redirectToEventBus(this);
        }

        public void attach(ServerProcess process) {
            this.process = process;
            process.getOe().redirect(agentRunner.oe);
        }

        public void detach() {
            if (process == null)
                return;
            agentRunner.oe.detach();
            process = null;
        }

        @Event.Subscriber(DelegateStream.IO.EventKey_Output)
        public void handleStdout(Event<String> e) {
            respond.convertAndSendToUser(user.getName(), "/console/output",
                    e.getData().replace("<","&lt;")
                            .replace(">","&gt;")
                            .replaceAll("\r?\n",ServerConnection.br));
        }

        @Event.Subscriber(DelegateStream.IO.EventKey_Error)
        public void handleStderr(Event<String> e) {
            respond.convertAndSendToUser(user.getName(), "/console/error",
                    e.getData().replace("<","&lt;")
                            .replace(">","&gt;")
                            .replaceAll("\r?\n",ServerConnection.br));
        }

        @Override
        @SneakyThrows
        public void closeSelf() {
            detach();
            connections.remove(user.getId());
            respond.convertAndSendToUser(user.getName(), "/console/disconnect", "");
            super.closeSelf();
        }
    }
}
