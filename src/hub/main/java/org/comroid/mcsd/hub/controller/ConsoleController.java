package org.comroid.mcsd.hub.controller;

import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.comroid.mcsd.core.entity.system.User;
import org.comroid.mcsd.core.repo.system.UserRepo;
import org.comroid.mcsd.hub.config.WebSocketConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.Map;

@Slf4j
@Controller
public class ConsoleController {
    @Autowired
    private SimpMessagingTemplate respond;
    @Autowired
    private UserRepo users;

    public User user(Map<String, Object> attr) {
        var session = (HttpSession) attr.get(WebSocketConfig.HTTP_SESSION_KEY);
        return users.get(session).get();
    }

    @MessageMapping("/console/connect")
    public void connect(@Header("simpSessionAttributes") Map<String, Object> attr) {}

    @MessageMapping("/console/input")
    public void input(@Header("simpSessionAttributes") Map<String, Object> attr, @Payload String input) {}

    @MessageMapping("/console/disconnect")
    public void disconnect(@Header("simpSessionAttributes") Map<String, Object> attr) {}
}
