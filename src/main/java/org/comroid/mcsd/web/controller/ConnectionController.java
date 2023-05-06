package org.comroid.mcsd.web.controller;


import jakarta.servlet.http.HttpSession;
import org.comroid.mcsd.web.entity.ShConnection;
import org.comroid.mcsd.web.entity.User;
import org.comroid.mcsd.web.exception.StatusCode;
import org.comroid.mcsd.web.exception.EntityNotFoundException;
import org.comroid.mcsd.web.repo.ServerRepo;
import org.comroid.mcsd.web.repo.ShRepo;
import org.comroid.mcsd.web.repo.UserRepo;
import org.comroid.mcsd.web.util.WebPagePreparator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.UUID;

@Controller
@RequestMapping("/connection")
public class ConnectionController {
    @Autowired
    private UserRepo users;
    @Autowired
    private ServerRepo servers;
    @Autowired
    private ShRepo shRepo;

    @GetMapping("/create")
    public String create(HttpSession session, Model model) {
        return new WebPagePreparator(model, "connection/edit", session)
                .setAttribute("creating", true)
                .setAttribute("editing", new ShConnection())
                .complete(User::canManageShConnections);
    }

    @PostMapping("/{id}")
    public String _edit(HttpSession session, @PathVariable UUID id, @ModelAttribute ShConnection con) {
        users.findBySession(session).require(User.Perm.ManageShConnections);
        if (!id.equals(con.getId()))
            throw new StatusCode(HttpStatus.BAD_REQUEST, "ID Mismatch");
        shRepo.save(con);
        return "redirect:/connection/" + con.getId();
    }

    @GetMapping("/{id}")
    public String view(HttpSession session, Model model, @PathVariable UUID id) {
        Optional<ShConnection> result = shRepo.findById(id);
        if (result.isEmpty())
            throw new EntityNotFoundException(ShConnection.class, id);
        return new WebPagePreparator(model, "connection/view", session)
                .setAttribute("connection", result.get())
                .complete(User::canManageShConnections);
    }
}
