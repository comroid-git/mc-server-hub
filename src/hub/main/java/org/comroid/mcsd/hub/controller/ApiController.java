package org.comroid.mcsd.hub.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.comroid.api.BitmaskAttribute;
import org.comroid.api.Polyfill;
import org.comroid.mcsd.api.dto.McsdConfig;
import org.comroid.mcsd.core.MCSD;
import org.comroid.mcsd.core.entity.*;
import org.comroid.mcsd.core.exception.EntityNotFoundException;
import org.comroid.mcsd.core.exception.StatusCode;
import org.comroid.mcsd.core.repo.*;
import org.comroid.util.Bitmask;
import org.comroid.util.Streams;
import org.comroid.util.Token;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
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
    @Autowired
    private DiscordBotRepo discordBotRepo;
    @Autowired
    private AuthorizationLinkRepo authorizationLinkRepo;
    @Autowired
    private McsdConfig config;

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

    @ResponseBody
    @GetMapping("/webapp/shells")
    public List<ShConnection> shells(HttpSession session) {
        return Streams.of(shRepo.findAll())
                .filter(x->x.hasPermission(user(session), AbstractEntity.Permission.Any))
                .toList();
    }

    @ResponseBody
    @GetMapping("/webapp/bots")
    public List<DiscordBot> bots(HttpSession session) {
        return Streams.of(discordBotRepo.findAll())
                .filter(x->x.hasPermission(user(session), AbstractEntity.Permission.Any))
                .toList();
    }

    @ResponseBody
    @PostMapping("/authorize")
    public String authorize(
            HttpSession session,
            @RequestParam("target") UUID targetId,
            @RequestParam("permissions") int permissions
    ) {
        var user = user(session);
        var entity = servers.findById(targetId)
                .map(Polyfill::<AbstractEntity>uncheckedCast)
                .or(() -> agents.findById(targetId))
                .or(() -> shRepo.findById(targetId))
                .or(() -> discordBotRepo.findById(targetId))
                .orElseThrow(() -> new EntityNotFoundException(AbstractEntity.class, targetId));
        entity.requirePermission(user, BitmaskAttribute.valueOf(permissions, AbstractEntity.Permission.class)
                .toArray(AbstractEntity.Permission[]::new));
        String code;
        do {
            code = Token.random(16, false);
        } while (authorizationLinkRepo.findById(code).isPresent());
        var link = new AuthorizationLink(code, user, entity.getId(), permissions);
        authorizationLinkRepo.save(link);
        return config.getHubBaseUrl() + link.getUrlPath();
    }

    @ResponseBody
    @GetMapping("/authorize")
    public String authorize(
            HttpSession session,
            @RequestParam("code") String code
    ) {
        var user = user(session);
        var link = authorizationLinkRepo.findById(code)
                .orElseThrow(() -> new EntityNotFoundException(AuthorizationLink.class, code));
        try {
            if (link.getValidUntil().isAfter(Instant.now())) {
                var entity = servers.findById(link.getTarget())
                        .map(Polyfill::<AbstractEntity>uncheckedCast)
                        .or(() -> agents.findById(link.getTarget()))
                        .or(() -> shRepo.findById(link.getTarget()))
                        .or(() -> discordBotRepo.findById(link.getTarget()))
                        .orElseThrow(() -> new EntityNotFoundException(AbstractEntity.class, link.getTarget()));
                entity.getPermissions().merge(user, link.getPermissions(), Bitmask::combine);
            } else return "The link has expired";
            return ("Successfully added the following permissions for " + user + " to " + link.getTarget() + ":\n\t- " +
                    Arrays.stream(AbstractEntity.Permission.values())
                            .filter(p -> p != AbstractEntity.Permission.Any)
                            .filter(p -> p.getAsInt() < 0x0100_0000)
                            .filter(p -> p.isFlagSet(link.getPermissions()))
                            .map(Enum::name)
                            .collect(Collectors.joining("\n\t- ")))
                    .replace("\n", "<br/>");
        } finally {
            authorizationLinkRepo.delete(link);
        }
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
