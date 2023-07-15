package org.comroid.mcsd.core.util;

import jakarta.servlet.http.HttpSession;
import org.comroid.api.Polyfill;
import org.comroid.mcsd.core.entity.User;
import org.comroid.mcsd.core.exception.InsufficientPermissionsException;
import org.comroid.mcsd.core.repo.ShRepo;
import org.comroid.mcsd.core.repo.ServerRepo;
import org.comroid.mcsd.core.repo.UserRepo;
import org.springframework.ui.Model;

import java.util.function.Predicate;
import java.util.stream.StreamSupport;

public class WebPagePreparator {
    private final Model model;
    private final String page;
    private String frame = "page/frame";

    public WebPagePreparator(Model model, String page, HttpSession session) {
        this.model = model;
        this.page = page;

        var users = ApplicationContextProvider.bean(UserRepo.class);
        var user = users.findBySession(session);
        var servers = ApplicationContextProvider.bean(ServerRepo.class);
        setAttribute("user", user);
        setAttribute("servers", StreamSupport.stream(servers.findByPermittedUser(users.findBySession(session).getId()).spliterator(), false).toList());
        setAttribute("connections", StreamSupport.stream(ApplicationContextProvider.bean(ShRepo.class).findAll().spliterator(), false).toList());
    }

    public WebPagePreparator frame(String frame) {
        this.frame = frame;
        return this;
    }

    public WebPagePreparator setAttribute(String name, Object value) {
        model.addAttribute(name, value);
        return this;
    }

    public <R> R getAttribute(String name) {
        return Polyfill.uncheckedCast(model.getAttribute(name));
    }

    public String complete() {
        return complete($ -> true);
    }

    public String complete(Predicate<User> permissionCheck) {
        User user = getAttribute("user");
        if (!permissionCheck.test(user))
            throw new InsufficientPermissionsException(user, User.Perm.None);
        setAttribute("page", page);
        return frame;
    }
}
