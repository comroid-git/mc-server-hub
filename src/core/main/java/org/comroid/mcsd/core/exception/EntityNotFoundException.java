package org.comroid.mcsd.core.exception;

import org.comroid.api.Command;

import java.util.UUID;

public class EntityNotFoundException extends Command.Error {
    public EntityNotFoundException(Class<?> type, UUID id) {
        super("Entity of type %s with ID %s not found".formatted(type.getName(), id));
    }
}
