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

    @PostMapping("/{cpId}/hard-reset")
    public ResponseEntity<Boolean> triggerHardReset(@PathVariable String cpId) {
        return ResponseEntity.ok(chargePointService.sendResetRequestToChargePoint(ResetType.Hard, cpId));
    }

    @PostMapping("/{cpId}/soft-reset")
    public ResponseEntity<Boolean> triggerSoftReset(@PathVariable String cpId) {
        return ResponseEntity.ok(chargePointService.sendResetRequestToChargePoint(ResetType.Soft, cpId));
    }

    @PostMapping("/{cpId}/connector-unlock")
    public ResponseEntity<Boolean> triggerConnectorUnlock(@PathVariable String cpId,
                                                          @RequestParam int connectorId) {
        return ResponseEntity.ok(chargePointService.sendConnectorUnlockToChargePoint(cpId, connectorId));
    }

    @PostMapping("/{cpId}/status-notification")
    public ResponseEntity<Boolean> triggerStatusNotificationRequest(@PathVariable String cpId,
                                                                    @RequestParam int connectorId) {
        return ResponseEntity.ok(chargePointService.sendTriggerMessageRequestToChargePoint(
                cpId, connectorId, TriggerMessageRequestType.StatusNotification
        ));
    }

    @PostMapping("/{cpId}/boot-notification")
    public ResponseEntity<Boolean> triggerBootNotificationRequest(@PathVariable String cpId,
                                                                  @RequestParam int connectorId) {
        return ResponseEntity.ok(chargePointService.sendTriggerMessageRequestToChargePoint(
                cpId, connectorId, TriggerMessageRequestType.BootNotification
        ));
    }

    @PostMapping("/{cpId}/heartbeat")
    public ResponseEntity<Boolean> triggerHeartBeatRequest(@PathVariable String cpId,
                                                           @RequestParam int connectorId) {
        return ResponseEntity.ok(chargePointService.sendTriggerMessageRequestToChargePoint(
                cpId, connectorId, TriggerMessageRequestType.Heartbeat
        ));
    }

    @PostMapping("/{cpId}/meter-values")
    public ResponseEntity<Boolean> triggerMeterValuesRequest(@PathVariable String cpId,
                                                           @RequestParam int connectorId) {
        return ResponseEntity.ok(chargePointService.sendTriggerMessageRequestToChargePoint(
                cpId, connectorId, TriggerMessageRequestType.MeterValues
        ));
    }

    @PostMapping("/{cpId}/change-configurations")
    public ResponseEntity<Boolean> triggerChangeConfigurationRequest(@PathVariable String cpId,
                                                                     @RequestParam String key,
                                                                     @RequestParam String value) {
        return ResponseEntity.ok(chargePointService.sendChangeConfigurationRequestToChargePoint(cpId, key, value));
    }

}
