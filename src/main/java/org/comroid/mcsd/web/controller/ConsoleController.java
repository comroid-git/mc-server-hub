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
        server.validateUserAccess(user, Server.Permission.Console);
        WebInterfaceConnection connection = new WebInterfaceConnection(user, server);
        connections.put(user.getId(), connection);
        respond.convertAndSendToUser(user.getName(), "/console/handshake", "\"%s\"".formatted(user.getId().toString()));
        connection.con.status().thenAccept(status -> respond.convertAndSendToUser(user.getName(), "/console/status", status));
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

    @MessageMapping("/console/backup")
    public void backup(@Header("simpSessionAttributes") Map<String, Object> sessionAttributes) {
        var session = (HttpSession) sessionAttributes.get(WebSocketConfig.HTTP_SESSION_KEY);
        var user = userRepo.findBySession(session);
        var res = connections.getOrDefault(user.getId(), null);
        if (res == null)
            throw new EntityNotFoundException(ShConnection.class, "User " + user.getId());
        if (!res.con.runBackup())
            log.error("Could not finish backup for server " + res.getServer().getName());
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
    private class WebInterfaceConnection implements Closeable {
        private final CompletableFuture<Void> connected = new CompletableFuture<>();
        private final ServerConnection con;
        private final Server server;
        private final User user;
        private final Channel channel;
        private final Input input;
        private final Output output;
        private final Output error;

        public WebInterfaceConnection(User user, Server server) throws JSchException {
            this.con = server.getConnection();
            this.server = server;
            this.user = user;
            this.channel = con.getSession().openChannel("shell");

            channel.setInputStream(this.input = new Input());
            channel.setOutputStream(this.output = new Output(false));
            channel.setExtOutputStream(this.error = new Output(true));

            channel.connect();
            connected.complete(null);
        }

        @Override
        public void close() {
            respond.convertAndSendToUser(user.getName(), "/console/disconnect", "");
            channel.disconnect();
        }

        public void input(String input) {
            synchronized (this.input.cmds) {
                this.input.cmds.add(input);
                this.input.cmds.notify();
            }
        }

        private class Input extends InputStream {
            private final Queue<String> cmds = new PriorityBlockingQueue<>(){{
                add(server.cmdAttach());
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
            private final boolean error;
            private StringWriter buf = new StringWriter();

            public Output(boolean error) {
                this.error = error;
            }

            @Override
            public void write(int b) {
                buf.write(b);
            }

            @Override
            public void flush() throws IOException {
                if (!connected.isDone())
                    connected.join();
                var str = Utils.removeAnsiEscapeSequences(buf.toString());
                respond.convertAndSendToUser(user.getName(), "/console/" + (error ? "error" : "output"), str);
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
