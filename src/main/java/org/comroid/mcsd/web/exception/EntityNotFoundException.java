package org.comroid.mcsd.web.exception;

import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.HttpStatusCodeException;

public class EntityNotFoundException extends HttpStatusCodeException {
    public EntityNotFoundException(Class<?> type, Object identifier) {
        super(HttpStatusCode.valueOf(404), "Entity of type %s with identifier %s not found".formatted(type.getName(),identifier));
    }
}
