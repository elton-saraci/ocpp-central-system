package com.ocppcentralsystem.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Payload to register or update a station.
 *
 * <p>On update the station is identified by the {@code cpId} in the query, so an operator can
 * rename everything else but not the identity the station connects with.</p>
 */
@Data
public class ChargePointRequest {

    @NotBlank(message = "is required")
    @Size(max = 100, message = "must not exceed 100 characters")
    private String cpId;

    @Size(max = 100, message = "must not exceed 100 characters")
    private String name;

    @Size(max = 100, message = "must not exceed 100 characters")
    private String vendor;

    @Size(max = 100, message = "must not exceed 100 characters")
    private String model;

    @Size(max = 100, message = "must not exceed 100 characters")
    private String serialNumber;

    @Size(max = 50, message = "must not exceed 50 characters")
    private String firmwareVersion;

    @Size(max = 50, message = "must not exceed 50 characters")
    private String timezone;

    @Size(max = 30, message = "must not have more than 30 entries")
    private Map<String, Object> metadata;

    /** Defaults to enabled when omitted. */
    private Boolean enabled;

    @Min(value = 1, message = "must be positive")
    private Integer heartbeatIntervalSeconds;

    @Valid
    private List<ConnectorRequest> connectors;
}
