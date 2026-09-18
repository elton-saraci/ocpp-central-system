package com.ocppcentralsystem.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * Outcome of setting or clearing a power limit.
 *
 * <p>{@code applied} is {@code false} when the charge point answered {@code Rejected} or
 * {@code NotSupported}: the request reached the charge point, it just refused it. A charge point
 * that could not be reached is reported as {@code 502} instead.</p>
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChargingProfileResultDTO {

    private String cpId;
    /** 0 means the whole charge point, i.e. every connector. */
    private Integer connectorId;
    /** The limit that was requested, in watts. Absent when a limit was cleared. */
    private Integer requestedPowerW;
    /** What was actually sent, in amperes, which is the unit charge points expect. */
    private Double limitAmps;
    private boolean applied;
    /** Status the charge point answered with. */
    private String status;
}
