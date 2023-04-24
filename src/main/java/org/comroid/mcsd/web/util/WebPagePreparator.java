package org.comroid.mcsd.web.util;

import jakarta.servlet.http.HttpSession;
import org.comroid.api.Polyfill;
import org.comroid.mcsd.web.entity.User;
import org.comroid.mcsd.web.exception.InsufficientPermissionsException;
import org.comroid.mcsd.web.repo.ServerRepo;
import org.comroid.mcsd.web.repo.UserRepo;
import org.springframework.ui.Model;

import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.StreamSupport;

public class WebPagePreparator {
    private final Model model;
    private final String page;
    private String frame = "page/frame";

    public WebPagePreparator(Model model, String page) {
        this.model = model;
        this.page = page;
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

    public WebPagePreparator session(HttpSession session, UserRepo users, ServerRepo servers) {
        var user = users.findBySession(session);
        setAttribute("user", user);
        setAttribute("servers", StreamSupport.stream(servers.findByPermittedUser(users.findBySession(session).getId()).spliterator(),false).toList());
        return this;
    }

    public String complete(Predicate<User> permissionCheck) {
        User user = getAttribute("user");
        if (!permissionCheck.test(user))
            throw new InsufficientPermissionsException(user, User.Perm.None);
        setAttribute("page", page);
        return frame;
    }
}
