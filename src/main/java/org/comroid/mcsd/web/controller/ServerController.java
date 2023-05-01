package org.comroid.mcsd.web.controller;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import me.dilley.MineStat;
import org.comroid.api.IntegerAttribute;
import org.comroid.mcsd.web.model.ServerConnection;
import org.comroid.mcsd.web.dto.StatusMessage;
import org.comroid.mcsd.web.entity.Server;
import org.comroid.mcsd.web.entity.ShConnection;
import org.comroid.mcsd.web.entity.User;
import org.comroid.mcsd.web.exception.BadRequestException;
import org.comroid.mcsd.web.exception.EntityNotFoundException;
import org.comroid.mcsd.web.repo.ServerRepo;
import org.comroid.mcsd.web.repo.ShRepo;
import org.comroid.mcsd.web.repo.UserRepo;
import org.comroid.mcsd.web.util.ApplicationContextProvider;
import org.comroid.mcsd.web.util.WebPagePreparator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

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
    void autoStart() {
        for (Server srv : servers.findAll()) {
            if (!srv.isAutoStart())
                continue;
            CompletableFuture.supplyAsync(() -> {
                log.info("Auto-Starting Server " + srv.getName());
                if (getStatus(srv).getStatus() != Server.Status.Offline)
                    return null;

                try {
                    if (!ServerConnection.send(srv, srv.cmdStart()))
                        log.warn("Starting server %s returned false".formatted(srv.getName()));
                    taskScheduler.scheduleWithFixedDelay(this::autoStart, Duration.ofMinutes(5));
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
        return getStatus(result);
    }

    @ResponseBody
    @GetMapping("/start/{id}")
    public boolean start(HttpSession session, @PathVariable UUID id) {
        var user = users.findBySession(session);
        var result = servers.findById(id).orElseThrow(() -> new EntityNotFoundException(Server.class, id));
        result.validateUserAccess(user, Server.Permission.Start);
        return ServerConnection.send(result, result.cmdStart());
    }

    @ResponseBody
    @GetMapping("/stop/{id}")
    public boolean stop(HttpSession session, @PathVariable UUID id) {
        var user = users.findBySession(session);
        var result = servers.findById(id).orElseThrow(() -> new EntityNotFoundException(Server.class, id));
        result.validateUserAccess(user, Server.Permission.Stop);
        return ServerConnection.send(result, result.cmdStop());
    }

    @ResponseBody
    @GetMapping("/backup/{id}")
    public boolean backup(HttpSession session, @PathVariable UUID id) {
        var user = users.findBySession(session);
        var result = servers.findById(id).orElseThrow(() -> new EntityNotFoundException(Server.class, id));
        result.validateUserAccess(user, Server.Permission.Backup);
        return ServerConnection.send(result, result.cmdBackup());
    }

    StatusMessage getStatus(Server srv) {
        log.debug("Getting status of Server " + srv.getName());
        var host = StreamSupport.stream(shRepo.findAll().spliterator(), false)
                .filter(con -> con.getId().equals(srv.getShConnection()))
                .findFirst()
                .orElseThrow(() -> new EntityNotFoundException(ShConnection.class, "Server " + srv.getName()))
                .getHost();
        var mc = new MineStat(host, srv.getPort(), 3);
        return new StatusMessage(
                srv.getId(),
                mc.isServerUp() ? Server.Status.Online : Server.Status.Offline,
                mc.getCurrentPlayers(),
                mc.getMaximumPlayers(),
                mc.getStrippedMotd()
        );
    }

    Properties updateProperties(Server srv, InputStream input) throws IOException {
        var prop = new Properties();
        prop.load(input);

        prop.setProperty("server-port", String.valueOf(srv.getPort()));
        prop.setProperty("max-players", String.valueOf(srv.getMaxPlayers()));

        // query
        prop.setProperty("enable-query", String.valueOf(true));
        prop.setProperty("query.port", String.valueOf(srv.getQueryPort()));

        // rcon
        prop.setProperty("enable-rcon", String.valueOf(true));
        prop.setProperty("rcon.port", String.valueOf(srv.getRConPort()));
        prop.setProperty("rcon.password", srv.getRConPassword());

        return prop;
    }
}
