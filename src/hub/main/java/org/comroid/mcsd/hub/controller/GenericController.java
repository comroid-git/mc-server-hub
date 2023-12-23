package org.comroid.mcsd.hub.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.comroid.api.IntegerAttribute;
import org.comroid.api.LongAttribute;
import org.comroid.mcsd.core.BasicController;
import org.comroid.mcsd.core.MCSD;
import org.comroid.mcsd.core.entity.*;
import org.comroid.mcsd.core.entity.server.Server;
import org.comroid.mcsd.core.entity.system.Agent;
import org.comroid.mcsd.core.entity.system.DiscordBot;
import org.comroid.mcsd.core.entity.system.ShConnection;
import org.comroid.mcsd.core.entity.system.User;
import org.comroid.mcsd.core.exception.EntityNotFoundException;
import org.comroid.mcsd.core.exception.InsufficientPermissionsException;
import org.comroid.mcsd.core.exception.BadRequestException;
import org.comroid.mcsd.core.repo.server.ServerRepo;
import org.comroid.mcsd.core.repo.system.*;
import org.comroid.util.Constraint;
import org.comroid.util.FormData;
import org.comroid.util.Streams;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
        model.addAttribute("user", user)
                .addAttribute("serverRepo", Streams.of(serverRepo.findAll())
                        .filter(x -> x.hasPermission(user, AbstractEntity.Permission.Administrate))
                        .toList())
                .addAttribute("discordBotRepo", Streams.of(discordBotRepo.findAll())
                        .filter(x -> x.hasPermission(user, AbstractEntity.Permission.Administrate))
                        .toList())
                .addAttribute("agentRepo", Streams.of(agentRepo.findAll())
                        .filter(x -> x.hasPermission(user, AbstractEntity.Permission.Administrate))
                        .toList())
                .addAttribute("shRepo", Streams.of(shRepo.findAll())
                        .filter(x -> x.hasPermission(user, AbstractEntity.Permission.Administrate))
                        .toList())
                .addAttribute("userRepo", Streams.of(userRepo.findAll())
                        .filter(x -> x.hasPermission(user, AbstractEntity.Permission.Administrate))
                        .toList())
                .addAttribute("canManageUsers", user.hasPermission(user, AbstractEntity.Permission.ManageUsers));
        return "dashboard";
    }

    @GetMapping("/server/view/{id}")
    public String serverView(Model model, HttpSession session, @PathVariable("id") UUID serverId) {
        var user = userRepo.get(session).assertion();
        var server = serverRepo.findById(serverId).orElseThrow(() -> new EntityNotFoundException(Server.class, serverId));
        model.addAttribute("user", user)
                .addAttribute("modules", Streams.of(mcsd.getModules().findAllByServerId(serverId)).toList())
                .addAttribute("target", server)
                .addAttribute("edit", false)
                .addAttribute("editKey", null);
        return "server/view";
    }

    @GetMapping("/user/view/{id}")
    public String userView(Model model, HttpSession session, @PathVariable("id") UUID userId) {
        var user = userRepo.get(session).assertion();
        var subject = userRepo.findById(userId).orElseThrow(() -> new EntityNotFoundException(User.class, userId));
        if (!user.canGovern(subject))
            throw new InsufficientPermissionsException(user, subject, AbstractEntity.Permission.ManageUsers);
        model.addAttribute("user", user)
                .addAttribute("target", subject)
                .addAttribute("canManageUsers", user.hasPermission(user, AbstractEntity.Permission.ManageUsers))
                .addAttribute("edit", false)
                .addAttribute("editKey", null);
        return "user/view";
    }

    @GetMapping("/{type}/edit/{id}")
    public String entityEdit(HttpSession session, Model model,
                             @PathVariable("type") String type,
                             @PathVariable("id") UUID id,
                             @RequestParam(value = "auth_code", required = false) String code) {
        var user = userRepo.get(session).assertion();
        user.verifyPermission(user, AbstractEntity.Permission.Modify)
                .or(authorizationLinkRepo.validate(user, id, code, AbstractEntity.Permission.Modify).cast())
                .orElseThrow(() -> new InsufficientPermissionsException(user, id, AbstractEntity.Permission.Modify));
        var target = core.findEntity(type, id);
        if (target instanceof Server)
            model.addAttribute("modules", Streams.of(mcsd.getModules().findAllByServerId(target.getId())).toList());
        model.addAttribute("user", user)
                .addAttribute("edit", true)
                .addAttribute("editKey", null)
                .addAttribute("target", target)
                .addAttribute("type", type);
        if (!type.equals("user"))
            model.addAttribute(type, target);
        return type + "/view";
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
                permissions|=perm;
            }
        }
        var user = userRepo.get(session).assertion();
        var subject = userRepo.findById(userId).orElseThrow(() -> new EntityNotFoundException(User.class, userId));
        var target = core.findEntity(type, targetId);
        model.addAttribute("user", user)
                .addAttribute("subject", subject)
                .addAttribute("permissions", Arrays.stream(AbstractEntity.Permission.values())
                        .filter(perm -> Stream.of(AbstractEntity.Permission.None, AbstractEntity.Permission.Any).noneMatch(perm::equals))
                        .sorted(Comparator.comparingLong(LongAttribute::getAsLong))
                        .toList())
                .addAttribute("mask", Objects.requireNonNullElse(target.getPermissions().get(subject), 0))
                .addAttribute("target", target)
                .addAttribute("type", type)
                .addAttribute(type, target);
        var verify = target.verifyPermission(user, AbstractEntity.Permission.Administrate);
        if (method == HttpMethod.POST && data.containsKey("auth_code"))
            verify = verify.or(authorizationLinkRepo.validate(user, targetId, data.get("auth_code").asString(), AbstractEntity.Permission.Administrate).cast());
        verify.orElseThrow(() -> new InsufficientPermissionsException(user, target, AbstractEntity.Permission.Administrate));
        if (method == HttpMethod.POST) {
            target.getPermissions().put(subject, permissions);
            switch(type){
                case"agent"->agentRepo.save((Agent)target);
                case"discordBot"->discordBotRepo.save((DiscordBot)target);
                case"server"->serverRepo.save((Server)target);
                case"sh"->shRepo.save((ShConnection)target);
                case"user"->userRepo.save((User)target);
                default -> throw new BadRequestException("invalid type: "+type);
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
        user.verifyPermission(user, AbstractEntity.Permission.Delete)
                .or(authorizationLinkRepo.validate(user, id, code, AbstractEntity.Permission.Delete).cast())
                .orElseThrow(() -> new InsufficientPermissionsException(user, id, AbstractEntity.Permission.Delete));
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
