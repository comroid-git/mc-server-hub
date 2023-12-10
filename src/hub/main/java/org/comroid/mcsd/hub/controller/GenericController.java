package org.comroid.mcsd.hub.controller;

import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Slf4j
@Controller
@RequestMapping
public class GenericController {
    @GetMapping
    public String dash(Model model, HttpSession session) {
        model.addAttribute("message", "THAT easy?");
        return "dashboard";
    }
}
