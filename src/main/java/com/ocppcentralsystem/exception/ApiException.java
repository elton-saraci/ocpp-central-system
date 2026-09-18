package com.ocppcentralsystem.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.List;

/**
 * Base class for errors that are deliberately reported to an API client.
 *
 * <p>Each exception carries the HTTP status to answer with and a stable, machine-readable
 * {@code code} (e.g. {@code TAG_NOT_FOUND}) that clients can branch on without parsing messages.</p>
 */
@Getter
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final List<String> details;

    protected ApiException(HttpStatus status, String code, String message) {
        this(status, code, message, null, null);
    }

    protected ApiException(HttpStatus status, String code, String message, List<String> details) {
        this(status, code, message, details, null);
    }

    protected ApiException(HttpStatus status, String code, String message, Throwable cause) {
        this(status, code, message, null, cause);
    }

    protected ApiException(HttpStatus status, String code, String message, List<String> details, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.code = code;
        this.details = details;
    }
}
