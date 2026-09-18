package com.ocppcentralsystem.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Result of {@code DELETE /charge-point}. A station with transaction history is disabled rather
 * than removed, so its past transactions keep a station to point at.
 */
@Data
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChargePointDeletionResultDTO {

    private String cpId;
    private ChargePointDeletionAction action;
    private long transactionCount;
    /** The station after the operation; {@code null} when it was deleted. */
    private ChargePointDTO chargePoint;
}
