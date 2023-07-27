package org.comroid.mcsd.agent.controller;

import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.comroid.api.Polyfill;
import org.comroid.api.StreamSupplier;
import org.comroid.mcsd.agent.dto.WebAppInfo;
import org.comroid.mcsd.core.entity.Agent;
import org.comroid.mcsd.core.entity.Server;
import org.comroid.mcsd.core.entity.User;
import org.comroid.mcsd.core.exception.EntityNotFoundException;
import org.comroid.mcsd.core.repo.ServerRepo;
import org.comroid.mcsd.core.repo.ShRepo;
import org.comroid.mcsd.core.repo.UserRepo;
import org.comroid.util.StreamUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Spliterators;
import java.util.UUID;
import java.util.stream.StreamSupport;

@Slf4j
@Controller
@RequestMapping("/api")
public class ApiController {
    @Autowired
    private UserRepo users;
    @Autowired
    private ServerRepo servers;
    @Autowired
    private Agent me;


    @ResponseBody
    @GetMapping("/webapp")
    public WebAppInfo getUser(HttpSession session) {
        return new WebAppInfo(
                users.findBySession(session),
                me,
                Polyfill.list(servers.findAllForAgent(me.getId()))
        );
    }
}
