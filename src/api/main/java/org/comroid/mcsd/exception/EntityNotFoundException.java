package org.comroid.mcsd.exception;

import org.springframework.http.HttpStatus;

public class EntityNotFoundException extends StatusCode {
    public EntityNotFoundException(Class<?> type, Object identifier) {
        super(HttpStatus.NOT_FOUND, "Entity of type %s with identifier %s not found".formatted(type.getName(), identifier));
    }
}
