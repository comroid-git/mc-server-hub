package org.comroid.mcsd.hub.controller;

import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.comroid.mcsd.core.entity.AbstractEntity;
import org.comroid.mcsd.core.entity.Server;
import org.comroid.mcsd.core.entity.User;
import org.comroid.mcsd.core.exception.EntityNotFoundException;
import org.comroid.mcsd.core.exception.InsufficientPermissionsException;
import org.comroid.mcsd.core.repo.ServerRepo;
import org.comroid.mcsd.core.repo.ShRepo;
import org.comroid.mcsd.core.repo.UserRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.UUID;

@Slf4j
@Controller
@RequestMapping("/api")
public class ApiController {
    @Autowired
    private UserRepo users;
    @Autowired
    private ServerRepo servers;
    @Autowired
    private ShRepo shRepo;

    @GetMapping("/findUserByName/{name}")
    public User findUserByName(@PathVariable String name) {
        return users.findByName(name).orElseThrow(() -> new EntityNotFoundException(User.class, name));
    }

    @GetMapping("/server/cron/{serverId}")
    public void triggerCron(HttpSession session, @PathVariable UUID serverId) {
        final var user = users.findBySession(session);
        servers.findById(serverId)
                .orElseThrow(() -> new EntityNotFoundException(Server.class, serverId))
                .verifyPermission(user, AbstractEntity.Permission.Administrate)
                .flatMap(Server.class)
                .orElseThrow(() -> new InsufficientPermissionsException(user, null, AbstractEntity.Permission.Administrate))
                .con()
                .cron();
    }
}
