package com.ocppcentralsystem.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class McpToolResponse {

    private final ObjectMapper objectMapper;

    public String from(ThrowingSupplier<?> supplier) {
        try {
            return objectMapper.writeValueAsString(supplier.get());
        } catch (Exception e) {
            return toErrorJson(e);
        }
    }

    public String successFrom(ThrowingBooleanSupplier supplier) {
        try {
            boolean success = supplier.getAsBoolean();
            return objectMapper.writeValueAsString(Map.of("success", success));
        } catch (Exception e) {
            return toErrorJson(e);
        }
    }

    private String toErrorJson(Exception e) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "success", false,
                    "error", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()
            ));
        } catch (Exception ignored) {
            return "{\"success\":false,\"error\":\"Unknown error\"}";
        }
    }

    @FunctionalInterface
    public interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    @FunctionalInterface
    public interface ThrowingBooleanSupplier {
        boolean getAsBoolean() throws Exception;
    }
}
