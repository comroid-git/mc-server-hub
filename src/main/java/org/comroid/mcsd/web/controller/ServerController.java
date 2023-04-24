package org.comroid.mcsd.web.controller;

import com.jcraft.jsch.JSch;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.comroid.api.IntegerAttribute;
import org.comroid.mcsd.web.dto.StatusMessage;
import org.comroid.mcsd.web.entity.Server;
import org.comroid.mcsd.web.entity.User;
import org.comroid.mcsd.web.exception.BadRequestException;
import org.comroid.mcsd.web.exception.EntityNotFoundException;
import org.comroid.mcsd.web.repo.ServerRepo;
import org.comroid.mcsd.web.repo.ShRepo;
import org.comroid.mcsd.web.repo.UserRepo;
import org.comroid.mcsd.web.util.WebPagePreparator;
import org.hibernate.engine.jdbc.ReaderInputStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.io.*;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Controller
@RequestMapping("/server")
public class ServerController {
    public static final Map<String, Integer> PERMS_MAP = Arrays.stream(Server.Permission.values())
            .collect(Collectors.toUnmodifiableMap(Enum::name, IntegerAttribute::getAsInt));
    @Autowired
    private UserRepo users;
    @Autowired
    private ServerRepo servers;
    @Autowired
    private ShRepo shRepo;

    @GetMapping("/create")
    public String create(HttpSession session, Model model) {
        return new WebPagePreparator(model, "server/edit")
                .session(session, users, servers)
                .setAttribute("creating", true)
                .setAttribute("editing", new Server())
                .setAttribute("shMap", shRepo.toShMap())
                .setAttribute("perms", PERMS_MAP)
                .setAttribute("scripts", List.of("/edit-server.js"))
                .setAttribute("load", "init()")
                .complete(User::canManageServers);
    }

    @GetMapping("/edit/{id}")
    public String edit(HttpSession session, Model model, @PathVariable UUID id) {
        var server = servers.findById(id).orElseThrow(() -> new EntityNotFoundException(Server.class, id));
        return new WebPagePreparator(model, "server/edit")
                .session(session, users, servers)
                .setAttribute("creating", false)
                .setAttribute("editing", server)
                .setAttribute("shMap", shRepo.toShMap())
                .setAttribute("perms", PERMS_MAP)
                .setAttribute("scripts", List.of("/edit-server.js"))
                .setAttribute("load", "init()")
                .complete(User::canManageServers);
    }

    @PostMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public String _edit(HttpSession session, @PathVariable UUID id, @RequestBody Server srv) {
        users.findBySession(session).require(User.Perm.ManageServers);
        if (!id.equals(srv.getId()))
            throw new BadRequestException("ID Mismatch");
        servers.save(srv);
        return "redirect:/server/" + srv.getId();
    }

    @GetMapping("/{id}")
    public String view(HttpSession session, Model model, @PathVariable UUID id) {
        var result = servers.findById(id).orElseThrow(() -> new EntityNotFoundException(Server.class, id));
        return new WebPagePreparator(model, "server/view")
                .session(session, users, servers)
                .setAttribute("server", result)
                .complete(User::canManageServers);
    }

    @GetMapping("/console/{id}")
    public String console(HttpSession session, Model model, @PathVariable UUID id) {
        var user = users.findBySession(session).require(User.Perm.ManageServers);
        var result = servers.findById(id).orElseThrow(() -> new EntityNotFoundException(Server.class, id));
        result.validateUserAccess(user, Server.Permission.Console);
        return new WebPagePreparator(model, "server/console")
                .session(session, users, servers)
                .setAttribute("server", result)
                .setAttribute("scripts", List.of(
                        "https://cdn.jsdelivr.net/gh/sockjs/sockjs-client@v0.3.4/dist/sockjs.js",
                        "https://cdn.jsdelivr.net/npm/stompjs@2.3.3/lib/stomp.js",
                        "/console.js",
                        "/controls.js"
                ))
                .setAttribute("load", "init()")
                .setAttribute("unload", "disconnect()")
                .complete(User::canManageServers);
    }

    @ResponseBody
    @GetMapping("/status/{id}")
    public StatusMessage status(HttpSession session, @PathVariable UUID id) {
        var user = users.findBySession(session).require(User.Perm.ManageServers);
        var result = servers.findById(id).orElseThrow(() -> new EntityNotFoundException(Server.class, id));
        result.validateUserAccess(user, Server.Permission.Status);
        return new StatusMessage(result.getId(), getStatus(result));
    }

    @ResponseBody
    @GetMapping("/start/{id}")
    public boolean start(HttpSession session, @PathVariable UUID id) {
        var user = users.findBySession(session).require(User.Perm.ManageServers);
        var result = servers.findById(id).orElseThrow(() -> new EntityNotFoundException(Server.class, id));
        result.validateUserAccess(user, Server.Permission.Start);

        //todo

        return false;
    }

    @ResponseBody
    @GetMapping("/stop/{id}")
    public boolean stop(HttpSession session, @PathVariable UUID id) {
        var user = users.findBySession(session).require(User.Perm.ManageServers);
        var result = servers.findById(id).orElseThrow(() -> new EntityNotFoundException(Server.class, id));
        result.validateUserAccess(user, Server.Permission.Stop);

        //todo

        return false;
    }

    Server.Status getStatus(Server srv) {
        return Server.Status.Unknown; // todo use server query
    }
}
