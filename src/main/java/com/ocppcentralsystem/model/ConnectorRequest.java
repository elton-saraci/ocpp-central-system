package com.ocppcentralsystem.model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ConnectorRequest {

    /** The OCPP connector number, 1 or higher. */
    @NotNull(message = "is required")
    @Min(value = 1, message = "must be 1 or more")
    private Integer connectorId;

    private ConnectorType type;
    private ConnectorFormat format;
    private PowerType powerType;

    @Min(value = 1, message = "must be positive")
    private Integer maxVoltage;

    @Min(value = 1, message = "must be positive")
    private Integer maxAmperage;

    @Min(value = 1, message = "must be positive")
    private Integer maxElectricPower;
}
