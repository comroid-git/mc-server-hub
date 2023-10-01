package org.comroid.mcsd.core.exception;

import org.comroid.api.Command;
import org.comroid.mcsd.core.entity.AbstractEntity;
import org.comroid.mcsd.core.entity.User;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

public class InsufficientPermissionsException extends Command.Error {
    public InsufficientPermissionsException(User user, @Nullable AbstractEntity related, AbstractEntity.Permission... missing) {
        super("User %s is missing permission %s for %s".formatted(user.getName(), Arrays.toString(missing), related));
    }
}
