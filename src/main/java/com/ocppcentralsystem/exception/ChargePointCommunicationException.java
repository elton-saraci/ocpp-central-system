package com.ocppcentralsystem.exception;

import org.springframework.http.HttpStatus;

/**
 * A request could not be delivered to the charge point: it is not connected, it did not answer in
 * time, or it answered with something unexpected. The central system is fine, the charge point
 * (or the link to it) is the problem - hence {@code 502 Bad Gateway}.
 */
public class ChargePointCommunicationException extends ApiException {

    public ChargePointCommunicationException(String message) {
        super(HttpStatus.BAD_GATEWAY, "CHARGE_POINT_UNREACHABLE", message);
    }

    public ChargePointCommunicationException(String message, Throwable cause) {
        super(HttpStatus.BAD_GATEWAY, "CHARGE_POINT_UNREACHABLE", message, cause);
    }
}
