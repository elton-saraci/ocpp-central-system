package com.ocppcentralsystem.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ChargeTransactionRequest {

    @NotBlank(message = "cpId is required")
    private String cpId;

    @NotNull(message = "connectorId is required")
    private int connectorId;

    @NotBlank(message = "idTag is required")
    private String idTag;
}
