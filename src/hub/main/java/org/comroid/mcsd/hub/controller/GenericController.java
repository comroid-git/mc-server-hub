package org.comroid.mcsd.hub.controller;

import jakarta.servlet.http.HttpSession;
import org.comroid.mcsd.core.repo.ShRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;
import java.util.stream.StreamSupport;

@Controller
public class GenericController {
    @Autowired
    private ShRepo shRepo;

    @GetMapping("/")
    public String dashboard(HttpSession session, Model model) {
        return new WebPagePreparator(model, "dashboard", session)
                .setAttribute("connections", StreamSupport.stream(shRepo.findAll().spliterator(), false).toList())
                .setAttribute("scripts", List.of("/dashboard.js"))
                .setAttribute("load", "start()")
                .complete();
    }
}
