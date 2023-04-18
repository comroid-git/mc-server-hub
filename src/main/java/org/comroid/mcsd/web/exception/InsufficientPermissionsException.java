package org.comroid.mcsd.web.exception;

import org.comroid.mcsd.web.entity.User;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.HttpStatusCodeException;

public class InsufficientPermissionsException extends HttpStatusCodeException {
    public InsufficientPermissionsException(User user, User.Perm missing) {
        super(HttpStatusCode.valueOf(403), "User %s is missing permission %s".formatted(user.getName(), missing));
    }
}
