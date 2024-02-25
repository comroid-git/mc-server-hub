package org.comroid.mcsd.hub.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.comroid.api.Polyfill;
import org.comroid.api.attr.LongAttribute;
import org.comroid.api.data.seri.DataNode;
import org.comroid.api.data.seri.DataStructure;
import org.comroid.api.data.seri.FormData;
import org.comroid.api.data.seri.JSON;
import org.comroid.api.func.ext.Wrap;
import org.comroid.api.func.util.Streams;
import org.comroid.api.info.Constraint;
import org.comroid.api.info.Maintenance;
import org.comroid.api.java.Activator;
import org.comroid.mcsd.core.BasicController;
import org.comroid.mcsd.core.MCSD;
import org.comroid.mcsd.core.ServerManager;
import org.comroid.mcsd.core.entity.AbstractEntity;
import org.comroid.mcsd.core.entity.module.ModulePrototype;
import org.comroid.mcsd.core.entity.server.Server;
import org.comroid.mcsd.core.entity.system.Agent;
import org.comroid.mcsd.core.entity.system.DiscordBot;
import org.comroid.mcsd.core.entity.system.ShConnection;
import org.comroid.mcsd.core.entity.system.User;
import org.comroid.mcsd.core.exception.BadRequestException;
import org.comroid.mcsd.core.exception.EntityNotFoundException;
import org.comroid.mcsd.core.exception.InsufficientPermissionsException;
import org.comroid.mcsd.core.model.ModuleType;
import org.comroid.mcsd.core.repo.server.ServerRepo;
import org.comroid.mcsd.core.repo.system.*;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.comroid.mcsd.core.entity.AbstractEntity.Permission.*;

@Slf4j
@Controller
@RequestMapping
public class GenericController {
    @Autowired
    private MCSD core;
    @Autowired
    private UserRepo userRepo;
    @Autowired
    private ServerRepo serverRepo;
    @Autowired
    private DiscordBotRepo discordBotRepo;
    @Autowired
    private AgentRepo agentRepo;
    @Autowired
    private ShRepo shRepo;
    @Autowired
    private AuthorizationLinkRepo authorizationLinkRepo;
    @Autowired
    private BasicController basicController;
    @Autowired
    private MCSD mcsd;
    @Autowired
    private ServerManager serverManager;

    /*
    @GetMapping("/error")
    @ExceptionHandler(Throwable.class)
    public String error(Model model, HttpSession session, HttpServletRequest request, Throwable exception) {
        var user = userRepo.get(session).assertion();
        model.addAttribute("user", user)
                .addAttribute("request", request)
                .addAttribute("exception", exception);
        return "error";
    }
     */

    @GetMapping
    public String dash(Model model, HttpSession session) {
        var user = userRepo.get(session).assertion();
        model.addAttribute("serverRepo", Streams.of(serverRepo.findAll())
                        .filter(x -> x.hasPermission(user, Administrate))
                        .collect(Collectors.toMap(Function.identity(), serverManager::tree)))
                .addAttribute("discordBotRepo", Streams.of(discordBotRepo.findAll())
                        .filter(x -> x.hasPermission(user, Administrate))
                        .toList())
                .addAttribute("agentRepo", Streams.of(agentRepo.findAll())
                        .filter(x -> x.hasPermission(user, Administrate))
                        .toList())
                .addAttribute("shRepo", Streams.of(shRepo.findAll())
                        .filter(x -> x.hasPermission(user, Administrate))
                        .toList())
                .addAttribute("userRepo", Streams.of(userRepo.findAll())
                        .filter(x -> x.hasPermission(user, Administrate))
                        .toList())
                .addAttribute("canManageUsers", user.hasPermission(user, ManageUsers));
        return "dashboard";
    }

    @GetMapping("/health")
    public String health(Model model, HttpSession session) {
        model.addAttribute("inspections", Maintenance.INSPECTIONS.stream()
                .collect(Collectors.toMap(Function.identity(), Maintenance.Inspection::getCheckResults)));
        return "health";
    }

    @ModelAttribute("user")
    public User modelUser(HttpSession session) {
        return userRepo.get(session).assertion();
    }

    @PostMapping("/module/add/{id}")
    public String addModules(HttpSession session, @PathVariable("id") UUID serverId) {
        var user = userRepo.get(session).assertion();
        var server = serverRepo.findById(serverId).orElseThrow(() -> new EntityNotFoundException(Server.class, serverId));
        server.requirePermission(user, ManageModules);

        return "redirect:/server/view/" + serverId;
    }

    @GetMapping("/server/modules/{id}")
    public String serverModulesPage(HttpSession session, Model model,
                                    @PathVariable("id") UUID serverId,
                                    @RequestParam(value = "auth_code", required = false) String code) {
        var user = userRepo.get(session).assertion();
        var server = core.getServers().findById(serverId)
                .orElseThrow(() -> new EntityNotFoundException(Server.class, serverId));
        server.verifyPermission(user, ManageModules)
                .or(authorizationLinkRepo.validate(user, serverId, code, ManageModules).castRef())
                .orElseThrow(() -> new InsufficientPermissionsException(user, serverId, ManageModules));
        model.addAttribute("user", user)
                .addAttribute("server", server)
                .addAttribute("struct", DataStructure.of(server.getClass()))
                .addAttribute("moduleTypes", ModuleType.cache)
                .addAttribute("modules", Streams.of(mcsd.getModules().findAllByServerId(server.getId()))
                        .collect(Collectors.toMap(ModulePrototype::getDtype, Function.identity())));
        return "server/modules";
    }

    @GetMapping("/{type}/{action}/{id}")
    public String entityPage(HttpSession session, Model model,
                             @PathVariable("type") String type,
                             @PathVariable("action") String action,
                             @PathVariable(value = "id", required = false) @Nullable UUID id,
                             @RequestParam(value = "auth_code", required = false) String code) {
        final var user = userRepo.get(session).assertion();
        final var create = action.equals("create");
        assert !create || id == null;
        final var perm = create ? switch (type) {
            case "server" -> CreateServer;
            case "agent" -> CreateAgent;
            case "sh" -> CreateSh;
            case "bot" -> CreateDiscordBot;
            default -> throw new IllegalStateException("Unexpected type: " + type);
        } : Modify;
        final var target = create ? null : core.findEntity(type, id);
        user.verifyPermission(user, perm)
                .or(() -> target instanceof User subject && user.canGovern(subject) ? subject : null)
                .or(id == null ? () -> null : authorizationLinkRepo.validate(user, id, code, perm).castRef())
                .orElseThrow(() -> new InsufficientPermissionsException(user, id, perm));
        if (target instanceof Server server)
            model.addAttribute("modules", Streams.of(mcsd.getModules().findAllByServerId(target.getId())).toList());
        model.addAttribute("user", user)
                .addAttribute("action", action)
                .addAttribute("editKey", code)
                .addAttribute("target", target)
                .addAttribute("type", type)
                .addAttribute("struct", DataStructure.of(core.findType(type)));
        return "entity/index";
    }

    @PostMapping("/api/webapp/{type}/{id}")
    public String entityUpdate(HttpSession session, Model model,
                               @PathVariable("type") String type,
                               @PathVariable(value = "id", required = false) @Nullable UUID id,
                               @RequestParam Map<String, String> data) {
        final var user = userRepo.get(session).assertion();
        final var create = data.getOrDefault("action", "edit").equals("create");
        final var target = create
                ? Activator.get(core.findType(type)).createInstance(DataNode.of(data))
                : core.findEntity(type, id);
        final var code = data.getOrDefault("auth_key", null);
        user.verifyPermission(user, Modify)
                .or(() -> target instanceof User subject && user.canGovern(subject) ? subject : null)
                .or(id == null ? () -> null : authorizationLinkRepo.validate(user, id, code, Modify).cast())
                .orElseThrow(() -> new InsufficientPermissionsException(user, id, Modify));
        var affected = DataStructure.of(target.getClass()).update(data, Polyfill.uncheckedCast(target));
        if (affected.isEmpty())
            log.debug("No properties of " + target + " were affected");
        else log.debug("Following properties of " + target + " were affected:"
                + affected.stream()
                .map(DataStructure.Member::getName)
                .collect(Collectors.joining("\n\t- ", "\n\t- ", "")));
        core.findRepository(type).save(target);
        return "redirect:/%s/view/%s".formatted(type, id);
    }

    @RequestMapping(value = "/{type}/permissions/{target}/{user}")
    public String entityPermissions(Model model, HttpSession session, HttpMethod method,
                                    @PathVariable("type") String type,
                                    @PathVariable("target") UUID targetId,
                                    @PathVariable("user") UUID userId,
                                    HttpServletRequest request
    ) throws IOException {
        Constraint.anyOf(method, "method", HttpMethod.GET, HttpMethod.POST).run();
        var permissions = 0L;
        FormData.@Nullable Object data = null;
        if (method == HttpMethod.POST) {
            data = FormData.Parser.parse(request.getReader().lines().collect(Collectors.joining("")));
            for (int perm : data.keySet()
                    .stream()
                    .filter(str -> str.startsWith("perm_"))
                    .map(str -> str.substring("perm_".length()))
                    .mapToInt(Integer::parseInt)
                    .toArray()) {
                permissions |= perm;
            }
        }
        var user = userRepo.get(session).assertion();
        var subject = userRepo.findById(userId).orElseThrow(() -> new EntityNotFoundException(User.class, userId));
        var target = core.findEntity(type, targetId);
        model.addAttribute("user", user)
                .addAttribute("subject", subject)
                .addAttribute("permissions", Arrays.stream(values())
                        .filter(perm -> Stream.of(None, Any).noneMatch(perm::equals))
                        .sorted(Comparator.comparingLong(LongAttribute::getAsLong))
                        .toList())
                .addAttribute("mask", Objects.requireNonNullElse(target.getPermissions().get(subject), 0))
                .addAttribute("target", target)
                .addAttribute("type", type)
                .addAttribute(type, target);
        var verify = target.verifyPermission(user, Administrate);
        if (method == HttpMethod.POST && data.containsKey("auth_code"))
            verify = verify.or(authorizationLinkRepo.validate(user, targetId, data.get("auth_code").asString(), Administrate).castRef());
        verify.orElseThrow(() -> new InsufficientPermissionsException(user, target, Administrate));
        if (method == HttpMethod.POST) {
            target.getPermissions().put(subject, permissions);
            switch (type) {
                case "agent" -> agentRepo.save((Agent) target);
                case "discordBot" -> discordBotRepo.save((DiscordBot) target);
                case "server" -> serverRepo.save((Server) target);
                case "sh" -> shRepo.save((ShConnection) target);
                case "user" -> userRepo.save((User) target);
                default -> throw new BadRequestException("invalid type: " + type);
            }
        }
        return "entity/permissions";
    }

    @RequestMapping(value = "/{type}/delete/{id}", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public String entityDelete(HttpSession session, Model model, HttpMethod method,
                               @PathVariable("type") String type,
                               @PathVariable("id") UUID id,
                               @Nullable @RequestParam(value = "auth_code", required = false) String code
    ) {
        Constraint.anyOf(method, "method", HttpMethod.GET, HttpMethod.POST).run();
        var user = userRepo.get(session).assertion();
        if (method == HttpMethod.GET) {
            var target = core.findEntity(type, id);
            model.addAttribute("user", user)
                    .addAttribute("target", target)
                    .addAttribute("type", type)
                    .addAttribute(type, target);
            return "entity/confirm_delete";
        }
        user.verifyPermission(user, Delete)
                .or(authorizationLinkRepo.validate(user, id, code, Delete).cast())
                .orElseThrow(() -> new InsufficientPermissionsException(user, id, Delete));
        (switch (type) {
            case "agent" -> agentRepo;
            case "discordBot" -> discordBotRepo;
            case "server" -> serverRepo;
            case "sh" -> shRepo;
            case "user" -> userRepo;
            default -> throw new BadRequestException("invalid type: " + type);
        }).deleteById(id);
        return "redirect:/";
    }
}
