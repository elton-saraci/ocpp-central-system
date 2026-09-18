package com.ocppcentralsystem.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * A station as the API presents it.
 *
 * <p>The session handle the OCPP transport uses internally is deliberately not exposed; clients
 * get {@link #connected} instead.</p>
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChargePointDTO {

    private String cpId;
    private String name;
    private String vendor;
    private String model;
    private String serialNumber;
    private String firmwareVersion;
    private String timezone;
    /** Free-form descriptive fields; nothing in the service reads them. */
    private Map<String, Object> metadata;
    private boolean enabled;
    private Integer heartbeatIntervalSeconds;

    private boolean connected;
    private WebsocketConnectionStatus connectionStatus;
    private LocalDateTime lastConnectedAt;
    private LocalDateTime lastUpdated;
    private LocalDateTime createdAt;

    private List<ConnectorDTO> connectors;
}
