package com.ocppcentralsystem.controller;

import com.ocppcentralsystem.model.ChargingProfileResultDTO;
import com.ocppcentralsystem.model.ChargingScheduleDTO;
import com.ocppcentralsystem.service.SmartChargingService;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Charging power limits, for a whole charge point or for a single connector.
 *
 * <p>The API speaks in watts and nothing else: the OCPP charging profile, its purpose, kind, stack
 * level and rate unit are assembled by {@link SmartChargingService}. {@code cpId} is a required
 * query parameter rather than a path variable for the same reason as on
 * {@link ChargePointController} - charge point identifiers may contain slashes. {@code connectorId}
 * is optional, and omitting it limits the whole charge point.</p>
 */
@Slf4j
@RestController
@RequestMapping("/charge-point/power")
@AllArgsConstructor
@Validated
public class SmartChargingController {

    private final SmartChargingService smartChargingService;

    /**
     * Limits how much power the charge point may draw, in watts. Omit {@code connectorId} to limit
     * the whole charge point; omit {@code durationMinutes} for a limit that stays until cleared.
     */
    @PutMapping
    public ResponseEntity<ChargingProfileResultDTO> setPowerLimit(
            @RequestParam String cpId,
            @RequestParam @Min(value = 0, message = "must be zero or more") int powerW,
            @RequestParam(required = false) Integer connectorId,
            @RequestParam(required = false) @Min(value = 0, message = "must be zero or more") Integer durationMinutes) {
        log.info("Setting power limit of cpId {} to {} W", cpId, powerW);
        return ResponseEntity.ok(smartChargingService.setPowerLimit(cpId, connectorId, powerW, durationMinutes));
    }

    /** Removes the power limit, so the charge point may draw whatever it wants again. */
    @DeleteMapping
    public ResponseEntity<ChargingProfileResultDTO> clearPowerLimit(
            @RequestParam String cpId,
            @RequestParam(required = false) Integer connectorId) {
        log.info("Clearing power limit of cpId {}", cpId);
        return ResponseEntity.ok(smartChargingService.clearPowerLimit(cpId, connectorId));
    }

    /** Reports the limit the charge point currently has in effect. */
    @GetMapping
    public ResponseEntity<ChargingScheduleDTO> getPowerLimit(
            @RequestParam String cpId,
            @RequestParam(required = false) Integer connectorId,
            @RequestParam(required = false) Integer durationMinutes) {
        log.info("Reading power limit of cpId {}", cpId);
        return ResponseEntity.ok(smartChargingService.getPowerLimit(cpId, connectorId, durationMinutes));
    }
}
