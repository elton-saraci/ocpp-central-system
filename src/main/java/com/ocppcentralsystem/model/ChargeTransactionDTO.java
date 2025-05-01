package com.ocppcentralsystem.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChargeTransactionDTO {
    private int chargeTransactionId;
    private String cpId;
    private Integer connectorId;
    private Integer meterStart;
    private Integer meterStop;
    private Integer latestMeterValue;
    private Integer latestPowerValue;
    private String idTag;
    private LocalDateTime lastUpdated;
    private boolean isActive;
}
