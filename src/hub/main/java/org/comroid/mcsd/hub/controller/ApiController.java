package org.comroid.mcsd.hub.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.comroid.api.BitmaskAttribute;
import org.comroid.api.Polyfill;
import org.comroid.mcsd.api.dto.McsdConfig;
import org.comroid.mcsd.api.dto.PlayerEvent;
import org.comroid.mcsd.api.dto.StatusMessage;
import org.comroid.mcsd.core.MCSD;
import org.comroid.mcsd.core.ServerManager;
import org.comroid.mcsd.core.entity.*;
import org.comroid.mcsd.core.entity.module.ModulePrototype;
import org.comroid.mcsd.core.entity.server.Server;
import org.comroid.mcsd.core.entity.system.*;
import org.comroid.mcsd.core.exception.EntityNotFoundException;
import org.comroid.mcsd.core.exception.InsufficientPermissionsException;
import org.comroid.mcsd.core.exception.BadRequestException;
import org.comroid.mcsd.core.exception.StatusCode;
import org.comroid.mcsd.core.module.player.PlayerEventModule;
import org.comroid.mcsd.core.repo.server.ServerRepo;
import org.comroid.mcsd.core.repo.system.*;
import org.comroid.util.Bitmask;
import org.comroid.util.Streams;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Controller
@RequestMapping("/api")
public class ApiController {
    @Qualifier("MCSD")
    @Autowired
    private MCSD core;
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
    @Autowired
    private ServerManager manager;

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
    @GetMapping("/webapp/modules")
    public List<ModulePrototype.Type> modules() {
        return Stream.of(ModulePrototype.Type.values()).toList();
    }

    @ResponseBody
    @GetMapping("/webapp/server/{id}/status")
    public StatusMessage fetchServerStatus(@PathVariable("id") UUID serverId) {
        var server = servers.findById(serverId)
                .orElseThrow(()->new EntityNotFoundException(Server.class, serverId));
        return server.status().join();
    }

    @PostMapping(value = "/webapp/server/edit", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public String editServer(HttpSession session, @RequestParam Map<String, String> data) {
        var user = user(session);
        var server = servers.findById(UUID.fromString(data.get("id")))
                .orElseThrow(()->new EntityNotFoundException(Server.class, data.get("id")));
        server.verifyPermission(user, AbstractEntity.Permission.Modify)
                .or(authorizationLinkRepo.validate(user, server.getId(), data.get("editKey"), AbstractEntity.Permission.Modify)
                        .map($->server))
                .peek($->authorizationLinkRepo.flush(data.get("editKey")))
                .orElseThrow(()->new InsufficientPermissionsException(user,server,AbstractEntity.Permission.Modify));

        try {
            if (data.containsKey("name")) server.setName(data.get("name"));
            if (data.containsKey("displayName")) server.setDisplayName(data.get("displayName"));
            if (data.containsKey("ownerId")) server.setOwner(users.findById(UUID.fromString(data.get("ownerId"))).orElseThrow(()->new EntityNotFoundException(User.class, data.get("ownerId"))));
            if (data.containsKey("mcVersion")) server.setMcVersion(data.get("mcVersion"));
            if (data.containsKey("host")) server.setHost(data.get("host"));
            if (data.containsKey("port")) server.setPort(Integer.parseInt(data.get("port")));
            if (data.containsKey("homepage")) server.setHomepage(data.get("homepage"));
            if (data.containsKey("managed")) server.setManaged(Boolean.parseBoolean(data.get("managed")));
            if (data.containsKey("enabled")) server.setEnabled(Boolean.parseBoolean(data.get("enabled")));
            if (data.containsKey("whitelist")) server.setWhitelist(Boolean.parseBoolean(data.get("whitelist")));
            if (data.containsKey("queryPort")) server.setQueryPort(Integer.parseInt(data.get("queryPort")));
            if (data.containsKey("rConPort")) server.setRConPort(Integer.parseInt(data.get("rConPort")));
            if (data.containsKey("rConPassword") && !data.get("rConPassword").isBlank()) server.setRConPassword(data.get("rConPassword"));
        } catch (NumberFormatException nfe) {
            throw new BadRequestException(nfe.getMessage());
        }
        return "redirect:/server/view/"+servers.save(server).getId();
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
        var link = authorizationLinkRepo.create(user, entity.getId(), permissions);
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

    @PostMapping("/agent/{agentId}/server/{serverId}/player/event")
    public void playerEvent(
            @PathVariable UUID agentId,
            @PathVariable UUID serverId,
            @NotNull @RequestHeader("Authorization") String token,
            @RequestBody PlayerEvent event
    ) {
        if (!agents.isTokenValid(agentId, token))
            throw new StatusCode(HttpStatus.UNAUTHORIZED, "Invalid token");
        manager.get(serverId).assertion("Server with ID " + serverId + " not found")
                .component(PlayerEventModule.class)
                .assertion("Server with ID " + serverId + " does not accept Player Events")
                .getBus()
                .publish(event);
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
