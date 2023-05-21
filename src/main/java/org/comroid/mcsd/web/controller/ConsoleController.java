package org.comroid.mcsd.web.controller;

import com.jcraft.jsch.JSchException;
import jakarta.servlet.http.HttpSession;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.comroid.api.DelegateStream;
import org.comroid.api.Event;
import org.comroid.mcsd.web.config.WebSocketConfig;
import org.comroid.mcsd.web.entity.Server;
import org.comroid.mcsd.web.entity.ShConnection;
import org.comroid.mcsd.web.entity.User;
import org.comroid.mcsd.web.exception.EntityNotFoundException;
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

import java.io.Closeable;
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
        respond.convertAndSendToUser(user.getName(), "/console/handshake", connection.con.status().join().withUserId(user.getId()));
    }

    @MessageMapping("/console/input")
    public void input(@Header("simpSessionAttributes") Map<String, Object> sessionAttributes, @Payload String input) {
        var session = (HttpSession) sessionAttributes.get(WebSocketConfig.HTTP_SESSION_KEY);
        var user = userRepo.findBySession(session);
        var res = connections.getOrDefault(user.getId(), null);
        if (res == null)
            throw new EntityNotFoundException(ShConnection.class, "User " + user.getId());
        res.con.getGame().input.accept(input.substring(1, input.length() - 1));
    }

    @SendToUser("/console/backup")
    @MessageMapping("/console/backup")
    public Object backup(@Header("simpSessionAttributes") Map<String, Object> sessionAttributes) {
        var session = (HttpSession) sessionAttributes.get(WebSocketConfig.HTTP_SESSION_KEY);
        var user = userRepo.findBySession(session);
        var res = connections.getOrDefault(user.getId(), null);
        if (res == null)
            throw new EntityNotFoundException(ShConnection.class, "User " + user.getId());
        return res.con.runBackup();
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
    @AllArgsConstructor
    private class WebInterfaceConnection implements Closeable {
        private final Server server;
        private final User user;
        private final ServerConnection con;
        private final DelegateStream.IO io;

        public WebInterfaceConnection(Server server, User user) {
            this.server = server;
            this.user = user;
            this.con = server.con();

            this.io = con.getGame().io.attach(
                    new DelegateStream.Output(txt -> respond.convertAndSendToUser(user.getName(), "/console/output", txt + ServerConnection.br)),
                    new DelegateStream.Output(txt -> respond.convertAndSendToUser(user.getName(), "/console/error", txt + ServerConnection.br)));

            con.log("web").info("Webinterface IO Configuration:\n"+io.getAlternateName());
         }

        @Override
        @SneakyThrows
        public void close() {
            respond.convertAndSendToUser(user.getName(), "/console/disconnect", "");
            io.close();
        }
    }
}
