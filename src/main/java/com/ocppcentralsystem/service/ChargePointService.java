package com.ocppcentralsystem.service;

import com.ocppcentralsystem.exception.ResourceNotFoundException;
import com.ocppcentralsystem.model.ChargePoint;
import com.ocppcentralsystem.repository.ChargePointRepository;
import eu.chargetime.ocpp.model.core.*;
import eu.chargetime.ocpp.model.remotetrigger.TriggerMessageConfirmation;
import eu.chargetime.ocpp.model.remotetrigger.TriggerMessageRequest;
import eu.chargetime.ocpp.model.remotetrigger.TriggerMessageRequestType;
import eu.chargetime.ocpp.model.remotetrigger.TriggerMessageStatus;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
@Slf4j
public class ChargePointService {

    private final ChargePointRepository chargePointRepository;
    private final ChargePointCommunicator chargePointCommunicator;

    public boolean sendResetRequestToChargePoint(String tenant, ResetType resetType, String cpId) {
        log.info("Reset request for cpId -> {}", cpId);
        ChargePoint chargePoint = requireChargePoint(tenant, cpId);

        ResetConfirmation confirmation = chargePointCommunicator.send(
                chargePoint.getWebsocketId(), new ResetRequest(resetType), ResetConfirmation.class);
        log.info("Reset confirmation for cpId {} -> {}", cpId, confirmation.getStatus());

        return ResetStatus.Accepted.equals(confirmation.getStatus());
    }

    public boolean sendConnectorUnlockToChargePoint(String tenant, String cpId, int connectorId) {
        log.info("ConnectorUnlock request for cpId -> {}, connectorId -> {}", cpId, connectorId);
        ChargePoint chargePoint = requireChargePoint(tenant, cpId);

        UnlockConnectorConfirmation confirmation = chargePointCommunicator.send(
                chargePoint.getWebsocketId(), new UnlockConnectorRequest(connectorId), UnlockConnectorConfirmation.class);
        log.info("ConnectorUnlock request confirmation for cpId {} -> {}", cpId, confirmation.getStatus());

        return UnlockStatus.Unlocked.equals(confirmation.getStatus());
    }

    public boolean sendTriggerMessageRequestToChargePoint(String tenant, String cpId, int connectorId,
                                                          TriggerMessageRequestType triggerMessageRequestType) {
        log.info("TriggerMessageRequest {} for cpId -> {}, connectorId -> {}", triggerMessageRequestType, cpId, connectorId);
        ChargePoint chargePoint = requireChargePoint(tenant, cpId);

        TriggerMessageRequest triggerMessageRequest = new TriggerMessageRequest(triggerMessageRequestType);
        triggerMessageRequest.setConnectorId(connectorId);

        TriggerMessageConfirmation confirmation = chargePointCommunicator.send(
                chargePoint.getWebsocketId(), triggerMessageRequest, TriggerMessageConfirmation.class);
        log.info("TriggerMessageRequest confirmation for cpId {} -> {}", cpId, confirmation.getStatus());

        return TriggerMessageStatus.Accepted.equals(confirmation.getStatus());
    }

    public boolean sendChangeConfigurationRequestToChargePoint(String tenant, String cpId, String key, String value) {
        log.info("ChangeConfigurationRequest for cpId -> {}, key -> {}, value -> {}", cpId, key, value);
        ChargePoint chargePoint = requireChargePoint(tenant, cpId);

        ChangeConfigurationConfirmation confirmation = chargePointCommunicator.send(
                chargePoint.getWebsocketId(), new ChangeConfigurationRequest(key, value), ChangeConfigurationConfirmation.class);
        log.info("ChangeConfigurationRequest confirmation for cpId {} -> {}", cpId, confirmation.getStatus());

        return ConfigurationStatus.Accepted.equals(confirmation.getStatus());
    }

    /**
     * @throws ResourceNotFoundException when no charge point is registered with that id in the
     *         tenant.
     */
    ChargePoint requireChargePoint(String tenant, String cpId) {
        return chargePointRepository.findByTenantAndCpId(tenant, cpId)
                .orElseThrow(() -> new ResourceNotFoundException("Charge point", cpId));
    }
}
