package com.ocppcentralsystem.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

/**
 * The limit a charge point currently has in effect, as reported by a composite schedule request.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChargingScheduleDTO {

    private String cpId;
    /** 0 means the whole charge point. */
    private Integer connectorId;
    /** The unit the charge point reported its limits in: {@code A} or {@code W}. */
    private String unit;
    private Integer durationSeconds;
    private List<ChargingSchedulePeriodDTO> periods;
    private String status;
}
