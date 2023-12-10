package org.comroid.mcsd.hub.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.comroid.mcsd.core.BasicController;
import org.comroid.mcsd.core.MCSD;
import org.comroid.mcsd.core.entity.AbstractEntity;
import org.comroid.mcsd.core.entity.Server;
import org.comroid.mcsd.core.entity.User;
import org.comroid.mcsd.core.exception.EntityNotFoundException;
import org.comroid.mcsd.core.exception.InsufficientPermissionsException;
import org.comroid.mcsd.core.exception.InvalidRequestException;
import org.comroid.mcsd.core.repo.*;
import org.comroid.util.Bitmask;
import org.comroid.util.Streams;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.data.repository.CrudRepository;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Objects;
import java.util.UUID;
import java.util.function.*;

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
                        .toList());
        return "dashboard";
    }

    @GetMapping("/server/view/{id}")
    public String serverView(Model model, HttpSession session, @PathVariable("id") UUID serverId) {
        var user = userRepo.get(session).assertion();
        var server = serverRepo.findById(serverId).orElseThrow(()->new EntityNotFoundException(Server.class,serverId));
        model.addAttribute("user", user)
                .addAttribute("server", server)
                .addAttribute("edit", false)
                .addAttribute("editKey", null);
        return "server/view";
    }

    @GetMapping("/{type}/edit/{id}")
    public String entityEdit(HttpSession session, Model model,
                             @PathVariable("type") String type,
                             @PathVariable("id") UUID id,
                             @RequestParam(value = "auth_code", required = false) String code) {
        var user = userRepo.get(session).assertion();
        user.verifyPermission(user, AbstractEntity.Permission.Modify)
                .or(authorizationLinkRepo.validate(user, id, code, AbstractEntity.Permission.Modify).cast())
                .orElseThrow(()->new InsufficientPermissionsException(user,id,AbstractEntity.Permission.Modify));
        var target = core.findEntity(type, id);
        model.addAttribute("user", user)
                .addAttribute("edit", true)
                .addAttribute("editKey", null)
                .addAttribute("target", target)
                .addAttribute("type", type)
                .addAttribute(type, target);
        return type+"/view";
    }

    @GetMapping("/{type}/permissions/{target}/{user}")
    @PostMapping("/{type}/permissions/{target}/{user}")
    public String entityPermissions(Model model, HttpSession session, HttpMethod method,
                              @PathVariable("type") String type,
                              @PathVariable("target") UUID targetId,
                              @PathVariable("user") UUID userId,
                              @RequestParam(value = "auth_code",required = false) String code
    ) {
        var user = userRepo.get(session).assertion();
        var subject = userRepo.findById(userId).orElseThrow(() -> new EntityNotFoundException(User.class, userId));
        var target = core.findEntity(type, targetId);
        model.addAttribute("user", user)
                .addAttribute("subject", subject)
                .addAttribute("permissions", basicController.permissions())
                .addAttribute("mask", Objects.requireNonNullElse(target.getPermissions().get(subject),0))
                .addAttribute("target", target)
                .addAttribute("type", type)
                .addAttribute(type, target);
        var verify = target.verifyPermission(user, AbstractEntity.Permission.Administrate);
        if (method == HttpMethod.POST)
            verify = verify.or(authorizationLinkRepo.validate(user, targetId, code, AbstractEntity.Permission.Administrate).cast());
        verify.orElseThrow(() -> new InsufficientPermissionsException(user, target, AbstractEntity.Permission.Administrate));
        if (method == HttpMethod.POST)
            throw new UnsupportedOperationException("unimplemented");//todo
        return "entity/permissions";
    }

    @GetMapping("/{type}/delete/{id}")
    @PostMapping("/{type}/delete/{id}")
    public String entityDelete(HttpSession session, Model model, HttpMethod method,
                             @PathVariable("type") String type,
                             @PathVariable("id") UUID id,
                             @RequestParam(value = "auth_code", required = false) String code) {
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
            default -> throw new InvalidRequestException("invalid type: " + type);
        }).deleteById(id);
        return "redirect:/";
    }
}
