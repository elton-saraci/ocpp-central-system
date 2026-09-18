package com.ocppcentralsystem.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import eu.chargetime.ocpp.model.core.ChargePointErrorCode;
import eu.chargetime.ocpp.model.core.ChargePointStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ConnectorDTO {

    private int connectorId;
    private ConnectorType type;
    private ConnectorFormat format;
    private PowerType powerType;
    /** Phases the server derives from the power type, used to convert watts to amperes. */
    private Integer phases;
    private Integer maxVoltage;
    private Integer maxAmperage;
    private Integer maxElectricPower;

    private ChargePointStatus status;
    private ChargePointErrorCode errorCode;
    private String statusInfo;
    private LocalDateTime lastStatusAt;
}
