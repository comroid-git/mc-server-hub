package org.comroid.mcsd.core;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.comroid.mcsd.core.exception.CommandStatusError;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.util.NestedServletException;

import java.io.PrintWriter;
import java.io.StringWriter;

@Controller
@ControllerAdvice
public class ErrorController implements org.springframework.boot.web.servlet.error.ErrorController {
    @GetMapping("/error")
    @ExceptionHandler(Throwable.class)
    public ErrorInfo error(HttpServletRequest request) {
        var ex = (Throwable) request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
        if (ex instanceof CommandStatusError)
            throw ((CommandStatusError) ex).toStatusCodeExc();
        var sw = new StringWriter();
        var pw = new PrintWriter(sw);
        //noinspection deprecation
        if (ex instanceof NestedServletException) {
            ex = ex.getCause();
            ex.printStackTrace(pw);
        }
        int code = (int) request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        String codeMessage = code + " - ";
        HttpStatus status = HttpStatus.resolve(code);
        if (status == null)
            codeMessage += "Internal Server Error";
        else codeMessage += status.getReasonPhrase();
        if (code == 404)
            codeMessage += ": " + request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        return new ErrorInfo(codeMessage,
                request.getAttribute(RequestDispatcher.ERROR_MESSAGE).toString(),
                sw.toString().replace("\r\n", "\n"));
    }

    public record ErrorInfo(String code, String message, String stacktrace) {}
}
