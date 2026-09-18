package com.ocppcentralsystem.controller;

import com.ocppcentralsystem.model.ChargePointDTO;
import com.ocppcentralsystem.model.ChargePointDeletionResultDTO;
import com.ocppcentralsystem.model.ChargePointRequest;
import com.ocppcentralsystem.service.ChargePointRegistryService;
import com.ocppcentralsystem.service.ChargePointService;
import eu.chargetime.ocpp.model.core.ResetType;
import eu.chargetime.ocpp.model.remotetrigger.TriggerMessageRequestType;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * The station registry and the remote operations that act on a connected station.
 *
 * <p>{@code cpId} is passed as a required query parameter instead of a path variable: charge
 * point identifiers are taken from the WebSocket URL and may contain slashes or other reserved
 * characters that cannot be expressed in a single path segment (Tomcat also rejects
 * percent-encoded slashes in a path by default). A single station is read by filtering the
 * collection with {@code ?cpId=}.</p>
 *
 * <p>Power limits live in {@link SmartChargingController}.</p>
 */
@Slf4j
@RestController
@RequestMapping("/charge-point")
@AllArgsConstructor
public class ChargePointController {

    private final ChargePointService chargePointService;
    private final ChargePointRegistryService chargePointRegistryService;

    /** Lists registered stations, optionally filtered by id or by whether they are enabled. */
    @GetMapping
    public ResponseEntity<List<ChargePointDTO>> findChargePoints(
            @RequestParam(required = false) String cpId,
            @RequestParam(required = false) Boolean enabled) {
        log.info("Listing stations, cpId -> {}, enabled -> {}", cpId, enabled);
        return ResponseEntity.ok(chargePointRegistryService.findAllStations(cpId, enabled));
    }

    /** Registers a station. Only registered stations may connect to this central system. */
    @PostMapping
    public ResponseEntity<ChargePointDTO> createChargePoint(@RequestBody @Valid ChargePointRequest request) {
        log.info("Registering station {}", request.getCpId());
        return ResponseEntity.status(HttpStatus.CREATED).body(chargePointRegistryService.createStation(request));
    }

    /** Updates a station. The {@code cpId} identifies it and cannot be changed. */
    @PutMapping
    public ResponseEntity<ChargePointDTO> updateChargePoint(@RequestParam String cpId,
                                                            @RequestBody @Valid ChargePointRequest request) {
        log.info("Updating station {}", cpId);
        return ResponseEntity.ok(chargePointRegistryService.updateStation(cpId, request));
    }

    /** Enables or disables a station. A disabled station is refused when it tries to connect. */
    @PatchMapping("/enabled")
    public ResponseEntity<ChargePointDTO> setChargePointEnabled(@RequestParam String cpId,
                                                                @RequestParam boolean enabled) {
        log.info("Setting station {} enabled -> {}", cpId, enabled);
        return ResponseEntity.ok(chargePointRegistryService.setStationEnabled(cpId, enabled));
    }

    /**
     * Removes a station. A station with transaction history is disabled instead, which the returned
     * {@code action} tells apart.
     */
    @DeleteMapping
    public ResponseEntity<ChargePointDeletionResultDTO> deleteChargePoint(@RequestParam String cpId) {
        log.info("Deleting station {}", cpId);
        return ResponseEntity.ok(chargePointRegistryService.deleteStation(cpId));
    }

    @PostMapping("/hard-reset")
    public ResponseEntity<Boolean> triggerHardReset(@RequestParam String cpId) {
        return ResponseEntity.ok(chargePointService.sendResetRequestToChargePoint(ResetType.Hard, cpId));
    }

    @PostMapping("/soft-reset")
    public ResponseEntity<Boolean> triggerSoftReset(@RequestParam String cpId) {
        return ResponseEntity.ok(chargePointService.sendResetRequestToChargePoint(ResetType.Soft, cpId));
    }

    @PostMapping("/connector-unlock")
    public ResponseEntity<Boolean> triggerConnectorUnlock(@RequestParam String cpId,
                                                          @RequestParam int connectorId) {
        return ResponseEntity.ok(chargePointService.sendConnectorUnlockToChargePoint(cpId, connectorId));
    }

    @PostMapping("/status-notification")
    public ResponseEntity<Boolean> triggerStatusNotificationRequest(@RequestParam String cpId,
                                                                    @RequestParam int connectorId) {
        return ResponseEntity.ok(chargePointService.sendTriggerMessageRequestToChargePoint(
                cpId, connectorId, TriggerMessageRequestType.StatusNotification
        ));
    }

    @PostMapping("/boot-notification")
    public ResponseEntity<Boolean> triggerBootNotificationRequest(@RequestParam String cpId,
                                                                  @RequestParam int connectorId) {
        return ResponseEntity.ok(chargePointService.sendTriggerMessageRequestToChargePoint(
                cpId, connectorId, TriggerMessageRequestType.BootNotification
        ));
    }

    @PostMapping("/heartbeat")
    public ResponseEntity<Boolean> triggerHeartBeatRequest(@RequestParam String cpId,
                                                           @RequestParam int connectorId) {
        return ResponseEntity.ok(chargePointService.sendTriggerMessageRequestToChargePoint(
                cpId, connectorId, TriggerMessageRequestType.Heartbeat
        ));
    }

    @PostMapping("/meter-values")
    public ResponseEntity<Boolean> triggerMeterValuesRequest(@RequestParam String cpId,
                                                             @RequestParam int connectorId) {
        return ResponseEntity.ok(chargePointService.sendTriggerMessageRequestToChargePoint(
                cpId, connectorId, TriggerMessageRequestType.MeterValues
        ));
    }

    @PostMapping("/change-configurations")
    public ResponseEntity<Boolean> triggerChangeConfigurationRequest(@RequestParam String cpId,
                                                                     @RequestParam String key,
                                                                     @RequestParam String value) {
        return ResponseEntity.ok(chargePointService.sendChangeConfigurationRequestToChargePoint(cpId, key, value));
    }
}
