package com.ocppcentralsystem.exception;

import org.springframework.http.HttpStatus;

/**
 * An entity that must be unique already exists, e.g. {@code TAG_ALREADY_EXISTS}.
 */
public class DuplicateResourceException extends ApiException {

    public DuplicateResourceException(String resource, Object id) {
        super(HttpStatus.CONFLICT,
                ResourceNotFoundException.code(resource, "ALREADY_EXISTS"),
                resource + " already exists: " + id);
    }
}
