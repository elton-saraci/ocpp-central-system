package com.ocppcentralsystem.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * A single period of the schedule a charge point reports back, converted for clients that think
 * in watts.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChargingSchedulePeriodDTO {

    /** Seconds from the start of the schedule at which this limit takes effect. */
    private Integer startPeriodSeconds;
    /** The raw limit the charge point reported, in {@code unit}. */
    private Double limit;
    /**
     * The same limit in watts. Derived from the configured phases and voltage when the charge
     * point reports in amperes.
     */
    private Double powerW;
}
