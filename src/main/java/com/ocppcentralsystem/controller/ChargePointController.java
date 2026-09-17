package com.ocppcentralsystem.controller;

import com.ocppcentralsystem.model.ChargePoint;
import com.ocppcentralsystem.service.ChargePointService;
import eu.chargetime.ocpp.model.core.ResetType;
import eu.chargetime.ocpp.model.remotetrigger.TriggerMessageRequestType;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Remote operations for charge points.
 *
 * <p>{@code cpId} is passed as a required query parameter instead of a path variable: charge
 * point identifiers are taken from the WebSocket URL and may contain slashes or other reserved
 * characters that cannot be expressed in a single path segment (Tomcat also rejects
 * percent-encoded slashes in a path by default).</p>
 */
@Slf4j
@RestController
@RequestMapping("/charge-point")
@AllArgsConstructor
public class ChargePointController {

    private final ChargePointService chargePointService;

    @GetMapping
    public ResponseEntity<List<ChargePoint>> findAllChargePoints() {
        return ResponseEntity.ok(chargePointService.getAllChargePoints());
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
