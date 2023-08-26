package org.comroid.mcsd.core.exception;

import org.comroid.mcsd.core.entity.AbstractEntity;
import org.comroid.mcsd.core.entity.Server;
import org.comroid.mcsd.core.entity.User;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.HttpStatus;

import java.util.Arrays;

public class InsufficientPermissionsException extends StatusCode {
    public InsufficientPermissionsException(User user, @Nullable AbstractEntity related, AbstractEntity.Permission... missing) {
        super(HttpStatus.UNAUTHORIZED, "User %s is missing permission %s for %s".formatted(user.getName(), Arrays.toString(missing), related));
    }
    @Deprecated
    public InsufficientPermissionsException(User user, User.Perm missing) {
        super(HttpStatus.UNAUTHORIZED, "User %s is missing permission %s".formatted(user.getName(), missing));
    }

    @Deprecated
    public InsufficientPermissionsException(User user, Server server, Server.Permission... insufficient) {
        super(HttpStatus.UNAUTHORIZED, "User %s is missing permissions for server %s: %s"
                .formatted(user, server, Arrays.toString(insufficient)));
    }
}
