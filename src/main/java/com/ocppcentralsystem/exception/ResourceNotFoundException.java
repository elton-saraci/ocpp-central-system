package com.ocppcentralsystem.exception;

import org.springframework.http.HttpStatus;

import java.util.Locale;

/**
 * A referenced entity does not exist, e.g. {@code TAG_NOT_FOUND}, {@code CHARGE_POINT_NOT_FOUND},
 * {@code CHARGE_TRANSACTION_NOT_FOUND}.
 */
public class ResourceNotFoundException extends ApiException {

    public ResourceNotFoundException(String resource, Object id) {
        super(HttpStatus.NOT_FOUND, code(resource, "NOT_FOUND"), resource + " not found: " + id);
    }

    static String code(String resource, String suffix) {
        return resource.toUpperCase(Locale.ROOT).replace(' ', '_') + "_" + suffix;
    }
}
