package com.ocppcentralsystem.service;

import com.ocppcentralsystem.model.ChargePoint;
import com.ocppcentralsystem.repository.ChargePointRepository;
import eu.chargetime.ocpp.JSONServer;
import eu.chargetime.ocpp.model.Confirmation;
import eu.chargetime.ocpp.model.core.*;
import eu.chargetime.ocpp.model.remotetrigger.TriggerMessageConfirmation;
import eu.chargetime.ocpp.model.remotetrigger.TriggerMessageRequest;
import eu.chargetime.ocpp.model.remotetrigger.TriggerMessageRequestType;
import eu.chargetime.ocpp.model.remotetrigger.TriggerMessageStatus;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@AllArgsConstructor
@Slf4j
public class ChargePointService {

    private final ChargePointRepository chargePointRepository;
    private final JSONServer jsonServer;

    public List<ChargePoint> getAllChargePoints() {
        return chargePointRepository.findAll();
    }

    public boolean sendResetRequestToChargePoint(ResetType resetType, String cpId) {
        try {
            log.info("Reset request for cpId -> {}", cpId);

            Optional<ChargePoint> optionalChargePoint = chargePointRepository.findById(cpId);
            if(optionalChargePoint.isEmpty()) {
                log.error("No cpId {} found", cpId);
                return false;
            }

            ResetRequest resetRequest = new ResetRequest(resetType);
            Confirmation abstractConfirmation = jsonServer.send(optionalChargePoint.get().getWebsocketId(), resetRequest)
                    .toCompletableFuture().get();
            ResetConfirmation resetConfirmation = (ResetConfirmation) abstractConfirmation;
            log.info("Reset confirmation for cpId {} -> {}", cpId, resetConfirmation.getStatus());

            return resetConfirmation.getStatus().equals(ResetStatus.Accepted);
        } catch (Exception ex) {
            log.error("Error occurred while triggering remote request, error message: " + ex.getLocalizedMessage());
            return false;
        }
    }

    public boolean sendConnectorUnlockToChargePoint(String cpId, int connectorId) {
        try {
            log.info("ConnectorUnlock request for cpId -> {}, connectorId -> {}", cpId, connectorId);

            Optional<ChargePoint> optionalChargePoint = chargePointRepository.findById(cpId);
            if(optionalChargePoint.isEmpty()) {
                log.error("No cpId {} found", cpId);
                return false;
            }

            UnlockConnectorRequest unlockConnectorRequest = new UnlockConnectorRequest(connectorId);
            Confirmation abstractConfirmation = jsonServer.send(optionalChargePoint.get().getWebsocketId(), unlockConnectorRequest)
                    .toCompletableFuture().get();
            UnlockConnectorConfirmation confirmation = (UnlockConnectorConfirmation) abstractConfirmation;
            log.info("ConnectorUnlock request confirmation for cpId {} -> {}", cpId, confirmation.getStatus());

            return confirmation.getStatus().equals(UnlockStatus.Unlocked);
        } catch (Exception ex) {
            log.error("Error occurred while triggering remote request, error message: " + ex.getLocalizedMessage());
            return false;
        }
    }

    public boolean sendTriggerMessageRequestToChargePoint(String cpId, int connectorId, TriggerMessageRequestType triggerMessageRequestType) {
        try {
            log.info("TriggerMessageRequest {} for cpId -> {}, connectorId -> {}", triggerMessageRequestType, cpId, connectorId);

            Optional<ChargePoint> optionalChargePoint = chargePointRepository.findById(cpId);
            if(optionalChargePoint.isEmpty()) {
                log.error("No cpId {} found", cpId);
                return false;
            }

            TriggerMessageRequest triggerMessageRequest = new TriggerMessageRequest(triggerMessageRequestType);
            triggerMessageRequest.setConnectorId(connectorId);

            Confirmation abstractConfirmation = jsonServer.send(optionalChargePoint.get().getWebsocketId(), triggerMessageRequest)
                    .toCompletableFuture().get();
            TriggerMessageConfirmation confirmation = (TriggerMessageConfirmation) abstractConfirmation;
            log.info("TriggerMessageRequest confirmation for cpId {} -> {}", cpId, confirmation.getStatus());

            return confirmation.getStatus().equals(TriggerMessageStatus.Accepted);
        } catch (Exception ex) {
            log.error("Error occurred while triggering remote request, error message: " + ex.getLocalizedMessage());
            return false;
        }
    }

    public boolean sendChangeConfigurationRequestToChargePoint(String cpId, String key, String value) {
        try {
            log.info("ChangeConfigurationRequest for cpId -> {}, key -> {}, value -> {}", cpId, key, value);

            Optional<ChargePoint> optionalChargePoint = chargePointRepository.findById(cpId);
            if(optionalChargePoint.isEmpty()) {
                log.error("No cpId {} found", cpId);
                return false;
            }

            ChangeConfigurationRequest changeConfigurationRequest = new ChangeConfigurationRequest(key, value);

            Confirmation abstractConfirmation = jsonServer.send(optionalChargePoint.get().getWebsocketId(), changeConfigurationRequest)
                    .toCompletableFuture().get();
            ChangeConfigurationConfirmation confirmation = (ChangeConfigurationConfirmation) abstractConfirmation;
            log.info("TriggerMessageRequest confirmation for cpId {} -> {}", cpId, confirmation.getStatus());

            return confirmation.getStatus().equals(ConfigurationStatus.Accepted);
        } catch (Exception ex) {
            log.error("Error occurred while triggering remote request, error message: " + ex.getLocalizedMessage());
            return false;
        }
    }

}
