package org.comroid.mcsd.hub.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.comroid.api.IntegerAttribute;
import org.comroid.api.Named;
import org.comroid.mcsd.core.MCSD;
import org.comroid.mcsd.core.entity.AbstractEntity;
import org.comroid.mcsd.core.entity.Agent;
import org.comroid.mcsd.core.entity.Server;
import org.comroid.mcsd.core.entity.User;
import org.comroid.mcsd.core.exception.EntityNotFoundException;
import org.comroid.mcsd.core.exception.StatusCode;
import org.comroid.mcsd.core.repo.AgentRepo;
import org.comroid.mcsd.core.repo.ServerRepo;
import org.comroid.mcsd.core.repo.ShRepo;
import org.comroid.mcsd.core.repo.UserRepo;
import org.comroid.util.Streams;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Controller
@RequestMapping("/api")
public class ApiController {
    @Autowired
    private UserRepo users;
    @Autowired
    private ServerRepo servers;
    @Autowired
    private AgentRepo agents;
    @Autowired
    private ShRepo shRepo;

    @ResponseBody
    @GetMapping("/webapp/user")
    public User user(HttpSession session) {
        return users.get(session).get();
    }

    @ResponseBody
    @GetMapping("/webapp/servers")
    public List<Server> servers(HttpSession session) {
        return Streams.of(servers.findAll())
                .filter(x->x.hasPermission(user(session), AbstractEntity.Permission.Any))
                .toList();
    }

    @ResponseBody
    @GetMapping("/webapp/agents")
    public List<Agent> agents(HttpSession session) {
        return Streams.of(agents.findAll())
                .filter(x->x.hasPermission(user(session), AbstractEntity.Permission.Any))
                .toList();
    }

    @GetMapping("/open/agent/hello/{id}")
    public void agentHello(
            @PathVariable UUID id,
            @Nullable @RequestParam(value = "baseUrl",required = false) String baseUrl,
            @NotNull @RequestHeader("Authorization") String token,
            HttpServletRequest request
    ) {
        if (!agents.isTokenValid(id, token))
            throw new StatusCode(HttpStatus.UNAUTHORIZED, "Invalid token");
        var $baseUrl = Optional.ofNullable(baseUrl)
                .or(() -> Optional.ofNullable(request.getHeader("X-Forwarded-Host"))
                        .or(() -> Optional.ofNullable(request.getHeader("Host")))
                        .or(() -> Optional.of(request.getRemoteHost()))
                        .map(MCSD::wrapHostname))
                .orElseThrow();
        final var agent = agents.findById(id).orElseThrow();
        if ($baseUrl.equals(agent.getBaseUrl()))
            return;
        log.info("Agent %s registered with new base url: %s".formatted(agent, baseUrl));
        agents.setBaseUrl(id, $baseUrl);
    }

    @GetMapping("/findUserByName/{name}")
    public User findUserByName(@PathVariable String name) {
        return users.findByName(name).orElseThrow(() -> new EntityNotFoundException(User.class, name));
    }
}
