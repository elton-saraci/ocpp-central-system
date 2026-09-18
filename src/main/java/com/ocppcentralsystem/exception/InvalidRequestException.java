package com.ocppcentralsystem.exception;

import org.springframework.http.HttpStatus;

import java.util.List;

/**
 * The request is well formed but asks for something that does not make sense, e.g. two connectors
 * with the same number. Reported as {@code 400 INVALID_REQUEST}.
 */
public class InvalidRequestException extends ApiException {

    public InvalidRequestException(String message, List<String> details) {
        super(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", message, details);
    }
}
