package com.ocppcentralsystem.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Turns exceptions into {@link ApiErrorResponse} bodies so every failure tells the client what
 * went wrong and which of its inputs caused it.
 *
 * <p>Anything that reaches the last handler is unexpected: it is logged with its stack trace and
 * reported as a generic {@code 500} so no internal detail leaks to the client.</p>
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrorResponse> handleApiException(ApiException ex, HttpServletRequest request) {
        if (ex.getStatus().is5xxServerError()) {
            log.error("{} {} failed with {}: {}", request.getMethod(), request.getRequestURI(), ex.getCode(), ex.getMessage(), ex);
        } else {
            log.warn("{} {} rejected with {}: {}", request.getMethod(), request.getRequestURI(), ex.getCode(), ex.getMessage());
        }
        return respond(ex.getStatus(), ex.getCode(), ex.getMessage(), ex.getDetails(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidBody(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList();
        return respond(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "The request body is not valid", details, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        List<String> details = ex.getConstraintViolations().stream()
                .map(violation -> parameterName(violation) + ": " + violation.getMessage())
                .toList();
        return respond(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "The request is not valid", details, request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingParameter(MissingServletRequestParameterException ex,
                                                                  HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, "MISSING_PARAMETER",
                ex.getMessage(), List.of(ex.getParameterName() + " is required"), request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                               HttpServletRequest request) {
        String expected = ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "the expected type";
        return respond(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER",
                ex.getName() + " is not valid", List.of(ex.getName() + " must be a " + expected), request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex,
                                                                HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, "MALFORMED_BODY",
                "The request body could not be read", List.of("Check the JSON syntax and the field types"), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        if (ex instanceof ErrorResponse errorResponse) {
            // Spring's own exceptions (unknown URL, unsupported method, ...) already carry a status.
            HttpStatus status = HttpStatus.resolve(errorResponse.getStatusCode().value());
            if (status == null) {
                status = HttpStatus.INTERNAL_SERVER_ERROR;
            }
            return respond(status, status.name(), status.getReasonPhrase(), null, request);
        }

        log.error("Unhandled exception for {} {}", request.getMethod(), request.getRequestURI(), ex);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "The request could not be processed", null, request);
    }

    private ResponseEntity<ApiErrorResponse> respond(HttpStatus status, String code, String message,
                                                     List<String> details, HttpServletRequest request) {
        return ResponseEntity.status(status).body(new ApiErrorResponse(
                LocalDateTime.now(),
                status.value(),
                code,
                message,
                request.getRequestURI(),
                details));
    }

    /**
     * Method validation reports a path such as {@code setPowerLimit.powerW}; only the parameter
     * name means anything to a client.
     */
    private String parameterName(ConstraintViolation<?> violation) {
        String path = violation.getPropertyPath().toString();
        int lastDot = path.lastIndexOf('.');
        return lastDot < 0 ? path : path.substring(lastDot + 1);
    }
}
