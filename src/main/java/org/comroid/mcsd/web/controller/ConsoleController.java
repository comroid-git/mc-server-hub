package org.comroid.mcsd.web.controller;

import com.jcraft.jsch.*;
import jakarta.servlet.http.HttpSession;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.comroid.mcsd.web.model.ServerConnection;
import org.comroid.mcsd.web.config.WebSocketConfig;
import org.comroid.mcsd.web.entity.Server;
import org.comroid.mcsd.web.entity.ShConnection;
import org.comroid.mcsd.web.entity.User;
import org.comroid.mcsd.web.exception.EntityNotFoundException;
import org.comroid.mcsd.web.repo.ServerRepo;
import org.comroid.mcsd.web.repo.UserRepo;
import org.comroid.mcsd.web.util.Utils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.io.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;

@Slf4j
@Controller
public class ConsoleController {
    private final Map<UUID, WebInterfaceConnection> connections = new ConcurrentHashMap<>();
    @Autowired
    private ServerController serverController;
    @Autowired
    private SimpMessagingTemplate respond;
    @Autowired
    private UserRepo userRepo;
    @Autowired
    private ServerRepo serverRepo;

    @MessageMapping("/console/connect")
    public void connect(@Header("simpSessionAttributes") Map<String, Object> attr, @Payload UUID serverId) {
        var session = (HttpSession) attr.get(WebSocketConfig.HTTP_SESSION_KEY);
        var user = userRepo.findBySession(session).require(User.Perm.ManageServers);
        var server = serverRepo.findById(serverId).orElseThrow(() -> new EntityNotFoundException(Server.class, serverId));
        WebInterfaceConnection connection = new WebInterfaceConnection(user, server);
        if (!connection.start()) {
            respond.convertAndSendToUser(user.getName(), "/console/handshake", "");
            return;
        }
        connections.put(user.getId(), connection);
        respond.convertAndSendToUser(user.getName(), "/console/handshake", "\"%s\"".formatted(user.getId().toString()));
        respond.convertAndSendToUser(user.getName(), "/console/status", serverController.getStatus(server));
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

    @MessageMapping("/console/disconnect")
    public void disconnect(@Header("simpSessionAttributes") Map<String, Object> sessionAttributes) {
        var session = (HttpSession) sessionAttributes.get(WebSocketConfig.HTTP_SESSION_KEY);
        var user = userRepo.findBySession(session);
        var res = connections.getOrDefault(user.getId(), null);
        if (res != null)
            res.close();
    }

    @Getter
    private class WebInterfaceConnection extends ServerConnection {
        private final CompletableFuture<Void> connected = new CompletableFuture<>();
        private final User user;
        private Channel channel;
        private Input input;
        private Output output;

        public WebInterfaceConnection(User user, Server server) {
            super(server);
            this.user = user;
        }

        protected boolean startConnection() throws Exception {
            this.channel = session.openChannel("shell");

            channel.setInputStream(this.input = new Input());
            channel.setOutputStream(this.output = new Output());

            channel.connect();
            connected.complete(null);
            return true;
        }

        @Override
        public void close() {
            respond.convertAndSendToUser(user.getName(), "/console/disconnect", "");
            channel.disconnect();
            super.close();
        }

        public void input(String input) {
            synchronized (this.input.cmds) {
                this.input.cmds.add(input);
                this.input.cmds.notify();
            }
        }

        private class Input extends InputStream {
            private final Queue<String> cmds = new PriorityBlockingQueue<>(){{
                add(server.attachCommand());
            }};
            private String cmd;
            private int r = 0;
            private boolean endlSent = true;

            @Override
            public int read() {
                if (cmd == null) {
                    if (endlSent)
                        endlSent = false;
                    else {
                        endlSent = true;
                        return -1;
                    }
                    synchronized (cmds) {
                        while (cmds.size() == 0) {
                            try {
                                cmds.wait();
                            } catch (InterruptedException e) {
                                log.error("Could not wait for new input", e);
                            }
                        }
                        cmd = cmds.poll();
                    }
                }

                int c;
                if (r < cmd.length())
                    c = cmd.charAt(r++);
                else {
                    cmd = null;
                    c = '\n';
                    r = 0;
                }
                return c;
            }
        }

        private class Output extends OutputStream {
            private StringWriter buf = new StringWriter();

            @Override
            public void write(int b) {
                buf.write(b);
            }

            @Override
            public void flush() throws IOException {
                if (!connected.isDone())
                    connected.join();
                var str = Utils.removeAnsiEscapeSequences(buf.toString());
                respond.convertAndSendToUser(user.getName(), "/console/output", str);
                buf.close();
                if (Arrays.stream(new String[]{"no screen to be resumed", "command not found", "Invalid operation"})
                        .anyMatch(str::contains)) {
                    WebInterfaceConnection.this.close();
                    return;
                }
                buf = new StringWriter();
            }
        }
    }
}
