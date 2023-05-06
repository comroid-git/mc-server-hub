package org.comroid.mcsd.web.controller;

import com.jcraft.jsch.JSchException;
import jakarta.servlet.http.HttpSession;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.comroid.mcsd.web.config.WebSocketConfig;
import org.comroid.mcsd.web.entity.Server;
import org.comroid.mcsd.web.entity.ShConnection;
import org.comroid.mcsd.web.entity.User;
import org.comroid.mcsd.web.exception.EntityNotFoundException;
import org.comroid.mcsd.web.model.AttachedConnection;
import org.comroid.mcsd.web.model.ServerConnection;
import org.comroid.mcsd.web.repo.ServerRepo;
import org.comroid.mcsd.web.repo.UserRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Controller
public class ConsoleController {
    private final Map<UUID, WebInterfaceConnection> connections = new ConcurrentHashMap<>();
    @Autowired
    private SimpMessagingTemplate respond;
    @Autowired
    private UserRepo userRepo;
    @Autowired
    private ServerRepo serverRepo;

    @MessageMapping("/console/connect")
    public void connect(@Header("simpSessionAttributes") Map<String, Object> attr, @Payload UUID serverId) throws JSchException {
        var session = (HttpSession) attr.get(WebSocketConfig.HTTP_SESSION_KEY);
        var user = userRepo.findBySession(session);
        var server = serverRepo.findById(serverId).orElseThrow(() -> new EntityNotFoundException(Server.class, serverId));
        server.requireUserAccess(user, Server.Permission.Console);
        WebInterfaceConnection connection = new WebInterfaceConnection(server, user);
        connections.put(user.getId(), connection);
        respond.convertAndSendToUser(user.getName(), "/console/handshake", "\"%s\"".formatted(user.getId().toString()));
        connection.status().thenAccept(status -> respond.convertAndSendToUser(user.getName(), "/console/status", status));
    }

    @MessageMapping("/console/input")
    public void input(@Header("simpSessionAttributes") Map<String, Object> sessionAttributes, @Payload String input) {
        var session = (HttpSession) sessionAttributes.get(WebSocketConfig.HTTP_SESSION_KEY);
        var user = userRepo.findBySession(session);
        var res = connections.getOrDefault(user.getId(), null);
        if (res == null)
            throw new EntityNotFoundException(ShConnection.class, "User " + user.getId());
        res.input(input.substring(1, input.length() - 1));
    }

    @SendToUser("/console/backup")
    @MessageMapping("/console/backup")
    public Object backup(@Header("simpSessionAttributes") Map<String, Object> sessionAttributes, @Payload ServerConnection.BackupMethod method) {
        var session = (HttpSession) sessionAttributes.get(WebSocketConfig.HTTP_SESSION_KEY);
        var user = userRepo.findBySession(session);
        var res = connections.getOrDefault(user.getId(), null);
        if (res == null)
            throw new EntityNotFoundException(ShConnection.class, "User " + user.getId());
        return res.runBackup(method);
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
    @ToString
    private class WebInterfaceConnection extends AttachedConnection {
        public final User user;

        public WebInterfaceConnection(Server server, User user) throws JSchException {
            super(server);

            this.user = user;
        }

        @Override
        protected void handleStdOut(String txt) {
            respond.convertAndSendToUser(user.getName(), "/console/output", txt + ServerConnection.br);
        }

        @Override
        protected void handleStdErr(String txt) {
            respond.convertAndSendToUser(user.getName(), "/console/error", txt + ServerConnection.br);
        }

        @Override
        public void close() {
            respond.convertAndSendToUser(user.getName(), "/console/disconnect", "");
            super.close();
        }
    }
}
