package org.comroid.mcsd.web.controller;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.comroid.api.IntegerAttribute;
import org.comroid.mcsd.web.model.ServerConnection;
import org.comroid.mcsd.web.dto.StatusMessage;
import org.comroid.mcsd.web.entity.Server;
import org.comroid.mcsd.web.entity.User;
import org.comroid.mcsd.web.exception.BadRequestException;
import org.comroid.mcsd.web.exception.EntityNotFoundException;
import org.comroid.mcsd.web.repo.ServerRepo;
import org.comroid.mcsd.web.repo.ShRepo;
import org.comroid.mcsd.web.repo.UserRepo;
import org.comroid.mcsd.web.util.WebPagePreparator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

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
    @Autowired
    private ThreadPoolTaskScheduler taskScheduler;

    @PostConstruct
    void runManageCycle() {
        for (Server srv : servers.findAll()) {
            if (!srv.isManaged())
                continue;
            CompletableFuture.supplyAsync(() -> {
                log.info("Auto-Starting Server %s".formatted(srv.getName()));
                var con = srv.getConnection();

                try {
                    // is it not offline?
                    if (con.status().join().getStatus() != Server.Status.Offline) {
                        log.info("Server %s did not need to be started".formatted(srv.getName()));
                        return null;
                    }

                    // manage server.properties file
                    if (!con.updateProperties())
                        log.warn("Unable to update server properties for server " + srv.getName());

                    /*
                    // upload most recent server.jar
                    final String prefix = "https://serverjars.com/api/fetchJar/";
                    String type = switch (srv.getMode()) {
                        case Vanilla -> "vanilla";
                        case Paper -> "servers";
                        case Forge, Fabric -> "modded";
                    };
                    String detail = srv.getMode().name().toLowerCase();
                    String version = srv.getMcVersion();
                    String url = prefix + type + "/" + detail + "/" + version;
                    log.info("Uploading most recent server.jar for %s from %s".formatted(srv.getName(), url));
                    try (var download = new URL(url).openStream();
                         var upload = ServerConnection.upload(srv, "server.jar")) {
                        download.transferTo(upload);
                    }
                     */

                    // start server
                    if (!con.uploadRunScript())
                        throw new RuntimeException("Unable to upload runscript to Server " + srv.getName());
                    log.info("Executing start cmd for Server " + srv.getName());
                    if (con.sendSh(srv.cmdStart()))
                        taskScheduler.scheduleWithFixedDelay(this::runManageCycle, Duration.ofMinutes(5));
                    else log.warn("Auto-Starting server %s did not finish successfully".formatted(srv.getName()));
                } catch (Exception e) {
                    log.error("Could not auto-start Server " + srv.getName(), e);
                }
                return null;
            });
        }
    }

    @GetMapping("/create")
    public String create(HttpSession session, Model model) {
        return new WebPagePreparator(model, "server/edit", session)
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
        return new WebPagePreparator(model, "server/edit", session)
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
        return new WebPagePreparator(model, "server/view", session)
                .setAttribute("server", result)
                .complete();
    }

    @GetMapping("/console/{id}")
    public String console(HttpSession session, Model model, @PathVariable UUID id) {
        var user = users.findBySession(session);
        var result = servers.findById(id).orElseThrow(() -> new EntityNotFoundException(Server.class, id));
        result.validateUserAccess(user, Server.Permission.Console);
        return new WebPagePreparator(model, "server/console", session)
                .setAttribute("server", result)
                .setAttribute("scripts", List.of(
                        "https://cdn.jsdelivr.net/gh/sockjs/sockjs-client@v0.3.4/dist/sockjs.js",
                        "https://cdn.jsdelivr.net/npm/stompjs@2.3.3/lib/stomp.js",
                        "/console.js",
                        "/controls.js"
                ))
                .setAttribute("load", "init()")
                .setAttribute("unload", "disconnect()")
                .complete();
    }

    @ResponseBody
    @GetMapping("/status/{id}")
    public StatusMessage status(HttpSession session, @PathVariable UUID id) {
        var user = users.findBySession(session);
        var result = servers.findById(id).orElseThrow(() -> new EntityNotFoundException(Server.class, id));
        result.validateUserAccess(user, Server.Permission.Status);
        return result.getConnection().status().join();
    }

    @ResponseBody
    @GetMapping("/start/{id}")
    public boolean start(HttpSession session, @PathVariable UUID id) {
        var user = users.findBySession(session);
        var result = servers.findById(id).orElseThrow(() -> new EntityNotFoundException(Server.class, id));
        result.validateUserAccess(user, Server.Permission.Start);
        return result.getConnection().sendSh(result.cmdStart());
    }

    @ResponseBody
    @GetMapping("/stop/{id}")
    public boolean stop(HttpSession session, @PathVariable UUID id) {
        var user = users.findBySession(session);
        var result = servers.findById(id).orElseThrow(() -> new EntityNotFoundException(Server.class, id));
        result.validateUserAccess(user, Server.Permission.Stop);
        return result.getConnection().sendSh(result.cmdStop());
    }

    @ResponseBody
    @GetMapping("/backup/{id}")
    public boolean backup(HttpSession session, @PathVariable UUID id) {
        var user = users.findBySession(session);
        var result = servers.findById(id).orElseThrow(() -> new EntityNotFoundException(Server.class, id));
        result.validateUserAccess(user, Server.Permission.Backup);
        return result.getConnection().sendSh(result.cmdBackup());
    }
}
