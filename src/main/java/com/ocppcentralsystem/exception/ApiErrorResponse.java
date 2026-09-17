package com.ocppcentralsystem.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;

/**
 * The single error shape every failing request answers with, so clients have one thing to parse.
 *
 * <pre>
 * {
 *   "timestamp": "2026-09-17T22:45:10.482",
 *   "status": 404,
 *   "code": "TAG_NOT_FOUND",
 *   "message": "Tag not found: RFID-9",
 *   "path": "/tag/RFID-9",
 *   "details": ["customerName is required"]
 * }
 * </pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(
        LocalDateTime timestamp,
        int status,
        String code,
        String message,
        String path,
        List<String> details) {
}
