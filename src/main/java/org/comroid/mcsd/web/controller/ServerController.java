package org.comroid.mcsd.web.controller;

import jakarta.servlet.http.HttpSession;
import org.comroid.mcsd.web.entity.Server;
import org.comroid.mcsd.web.entity.ShConnection;
import org.comroid.mcsd.web.entity.User;
import org.comroid.mcsd.web.exception.BadRequestException;
import org.comroid.mcsd.web.exception.EntityNotFoundException;
import org.comroid.mcsd.web.repo.ServerRepo;
import org.comroid.mcsd.web.repo.ShRepo;
import org.comroid.mcsd.web.repo.UserRepo;
import org.comroid.mcsd.web.util.WebPagePreparator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Controller
@RequestMapping("/server")
public class ServerController {
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
                .complete(User::canManageServers);
    }

    @PostMapping("/{id}")
    public String _edit(HttpSession session, @PathVariable UUID id, @ModelAttribute Server srv) {
        users.findBySession(session).require(User.Perm.ManageServers);
        if (!id.equals(srv.getId()))
            throw new BadRequestException("ID Mismatch");
        servers.save(srv);
        return "redirect:/server/" + srv.getId();
    }

    @GetMapping("/{id}")
    public String view(HttpSession session, Model model, @PathVariable UUID id) {
        Optional<Server> result = servers.findById(id);
        if (result.isEmpty())
            throw new EntityNotFoundException(Server.class, id);
        return new WebPagePreparator(model, "server/view")
                .session(session, users, servers)
                .setAttribute("server", result.get())
                .complete(User::canManageServers);
    }

    @GetMapping("/{id}/console")
    public String console(HttpSession session, Model model, @PathVariable UUID id) {
        Optional<Server> result = servers.findById(id);
        if (result.isEmpty())
            throw new EntityNotFoundException(Server.class, id);
        return new WebPagePreparator(model, "server/console")
                .session(session, users, servers)
                .setAttribute("server", result.get())
                .setAttribute("scripts", List.of(
                        "https://cdn.jsdelivr.net/gh/sockjs/sockjs-client@v0.3.4/dist/sockjs.js",
                        "https://cdn.jsdelivr.net/npm/stompjs@2.3.3/lib/stomp.js",
                        "/console.js"
                ))
                .setAttribute("load", "init()")
                .setAttribute("unload", "disconnect()")
                .complete(User::canManageServers);
    }
}
