package com.ocppcentralsystem.exception;

import org.springframework.http.HttpStatus;

/**
 * An entity that must be unique already exists, e.g. {@code TAG_ALREADY_EXISTS}.
 */
public class DuplicateResourceException extends ApiException {

    public DuplicateResourceException(String resource, Object id) {
        this(resource, id, null);
    }

    /**
     * @param reason why the value is taken, for cases the caller cannot tell from the id alone,
     *               e.g. an identifier another tenant already holds.
     */
    public DuplicateResourceException(String resource, Object id, String reason) {
        super(HttpStatus.CONFLICT,
                ResourceNotFoundException.code(resource, "ALREADY_EXISTS"),
                resource + " already exists: " + id + (reason == null ? "" : " (" + reason + ")"));
    }
}
