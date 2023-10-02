package org.comroid.mcsd.core;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.comroid.api.info.Log;
import org.comroid.mcsd.core.exception.CommandStatusError;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.util.NestedServletException;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;

@Controller
@ControllerAdvice
public class ErrorController implements org.springframework.boot.web.servlet.error.ErrorController {
    @Deprecated
    @GetMapping("/error")
    public ErrorInfo error(HttpServletRequest request) {
        var ex = (Throwable) request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
        int code = (int) Objects.requireNonNullElse(request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE),500);
        var uri = Optional.ofNullable(request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI)).map(Object::toString).orElse(null);
        var msg = Optional.ofNullable(request.getAttribute(RequestDispatcher.ERROR_MESSAGE)).map(Object::toString).orElse(null);
        return ErrorInfo.create(ex,code,uri,msg);
    }

    @ExceptionHandler({ Throwable.class })
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ResponseEntity<ErrorInfo> handleError(final Throwable t) {
        Log.at(Level.FINE, "Internal Server Error", t);
        return new ResponseEntity<>(ErrorInfo.create(t,500,"Internal Server Error",null), HttpStatusCode.valueOf(500));
    }

    public record ErrorInfo(String code, String message, String stacktrace) {
        public static ErrorInfo create(Throwable ex, int code, @Nullable String message, @Nullable String requestUri) {
            if (ex instanceof CommandStatusError)
                throw ((CommandStatusError) ex).toStatusCodeExc();
            var sw = new StringWriter();
            var pw = new PrintWriter(sw);
            //noinspection deprecation
            if (ex instanceof NestedServletException) {
                ex = ex.getCause();
                ex.printStackTrace(pw);
            }
            String codeMessage = code + " - ";
            HttpStatus status = HttpStatus.resolve(code);
            if (status == null)
                codeMessage += "Internal Server Error";
            else codeMessage += status.getReasonPhrase();
            if (code == 404)
                codeMessage += ": " + requestUri;
            return new ErrorInfo(codeMessage, message, sw.toString().replace("\r\n", "\n"));
        }
    }
}