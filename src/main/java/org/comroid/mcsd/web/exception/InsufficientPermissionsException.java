package org.comroid.mcsd.web.exception;

import org.comroid.mcsd.web.entity.Server;
import org.comroid.mcsd.web.entity.User;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.HttpStatusCodeException;

import java.util.Arrays;

public class InsufficientPermissionsException extends HttpStatusCodeException {
    public InsufficientPermissionsException(User user, User.Perm missing) {
        super(HttpStatusCode.valueOf(403), "User %s is missing permission %s".formatted(user.getName(), missing));
    }

    public InsufficientPermissionsException(User user, Server server, Server.Permission... insufficient) {
        super(HttpStatusCode.valueOf(403), "User %s is missing permissions for server %s: %s"
                .formatted(user,server, Arrays.toString(insufficient)));
    }
}
