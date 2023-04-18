package org.comroid.mcsd.web.exception;

import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.HttpStatusCodeException;

public class BadRequestException extends HttpStatusCodeException {
    public BadRequestException(String reason) {
        super(HttpStatusCode.valueOf(400), reason);
    }
}
