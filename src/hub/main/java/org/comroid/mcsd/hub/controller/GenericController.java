package org.comroid.mcsd.hub.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.comroid.mcsd.core.MCSD;
import org.comroid.mcsd.core.entity.AbstractEntity;
import org.comroid.mcsd.core.entity.Server;
import org.comroid.mcsd.core.entity.User;
import org.comroid.mcsd.core.exception.EntityNotFoundException;
import org.comroid.mcsd.core.exception.InsufficientPermissionsException;
import org.comroid.mcsd.core.repo.*;
import org.comroid.util.Streams;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

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

    @GetMapping("/permissions/{type}/{user}/{target}")
    public String permissions(Model model, HttpSession session,
                              @PathVariable("type") String type,
                              @PathVariable("user") UUID userId,
                              @PathVariable("target") UUID targetId,
                              @RequestParam(value = "code",required = false) String code
    ) {
        var exec = userRepo.get(session).assertion();
        var user = userRepo.findById(userId).orElseThrow(() -> new EntityNotFoundException(User.class, userId));
        var target = core.findEntity(type, targetId);
        target.verifyPermission(exec, AbstractEntity.Permission.Administrate)
                .or(authorizationLinkRepo.validate(exec, targetId, code, AbstractEntity.Permission.Administrate).cast())
                .orElseThrow(() -> new InsufficientPermissionsException(exec, target, AbstractEntity.Permission.Administrate));
        model.addAttribute("user", user)
                .addAttribute(type, target);
        return "entity/permissions";
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
}
