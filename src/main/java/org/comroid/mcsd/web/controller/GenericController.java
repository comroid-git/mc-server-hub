package org.comroid.mcsd.web.controller;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.comroid.mcsd.web.repo.ServerRepo;
import org.comroid.mcsd.web.repo.ShRepo;
import org.comroid.mcsd.web.repo.UserRepo;
import org.comroid.mcsd.web.util.WebPagePreparator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.util.NestedServletException;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Optional;
import java.util.stream.StreamSupport;

@Controller
public class GenericController implements ErrorController {
    @Autowired
    private UserRepo users;
    @Autowired
    private ServerRepo servers;
    @Autowired
    private ShRepo shRepo;

    @GetMapping("/")
    public String dashboard(HttpSession session, Model model) {
        return new WebPagePreparator(model, "dashboard")
                .session(session, users, servers)
                .setAttribute("servers", users.findBySession(session)
                        .getPermittedServers()
                        .stream()
                        .map(servers::findById)
                        .flatMap(Optional::stream)
                        .toList())
                .setAttribute("connections", StreamSupport.stream(shRepo.findAll().spliterator(), false).toList())
                .complete(u->true);
    }

    @GetMapping("/error")
    public String error(Model model, HttpSession session, HttpServletRequest request) {
        var ex = (Throwable) request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
        var sw = new StringWriter();
        var pw = new PrintWriter(sw);
        //noinspection deprecation
        if (ex instanceof NestedServletException) {
            ex = ex.getCause();
            ex.printStackTrace(pw);
        }
        int code = (int)request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        String codeMessage = code + " - ";
        HttpStatus status = HttpStatus.resolve(code);
        if (status == null)
            codeMessage += "Internal Server Error";
        else codeMessage += status.getReasonPhrase();
        if (code == 404)
            codeMessage += ": " + request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        return new WebPagePreparator(model, "generic/error")
                .session(session, users, servers)
                .setAttribute("code", codeMessage)
                .setAttribute("message", request.getAttribute(RequestDispatcher.ERROR_MESSAGE))
                .setAttribute("stacktrace", sw.toString().replace("\r\n", "\n"))
                .complete($->true);
    }
}
