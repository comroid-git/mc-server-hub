package org.comroid.mcsd.web.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.HttpStatusCodeException;

public class EntityNotFoundException extends StatusCode {
    public EntityNotFoundException(Class<?> type, Object identifier) {
        super(HttpStatus.NOT_FOUND, "Entity of type %s with identifier %s not found".formatted(type.getName(), identifier));
    }
}
