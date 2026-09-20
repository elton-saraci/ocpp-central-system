package com.ocppcentralsystem.ocpp;

import com.ocppcentralsystem.service.ChargePointRegistryService;
import com.ocppcentralsystem.util.ChargePointIdentity;
import eu.chargetime.ocpp.JSONServer;
import eu.chargetime.ocpp.ServerEvents;
import eu.chargetime.ocpp.model.SessionInformation;
import eu.chargetime.ocpp.model.remotetrigger.TriggerMessageRequest;
import eu.chargetime.ocpp.model.remotetrigger.TriggerMessageRequestType;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Connection gate for charge points. Once the WebSocket handshake is done the library tells us
 * about the new session; only a station that is registered and enabled is allowed to stay, every
 * other one is disconnected on the spot.
 */
@Component
@Slf4j
@AllArgsConstructor
public class OcppSessionEvents implements ServerEvents {

    private final JSONServer jsonServer;
    private final ChargePointRegistryService chargePointRegistryService;

    /*
    Once the WebSocket handshake is done the newSession method gets called. Only a station that
    is registered and enabled is allowed to stay - anything else is disconnected on the spot.
    We trigger the BootNotification and StatusNotifications upon session creation.
     */
    @Override
    public void newSession(UUID websocketId, SessionInformation information) {
        try {
            String cpId = ChargePointIdentity.fromPath(information.getIdentifier());
            log.info("New Session established, sessionIndex -> {}, sessionIdentifier -> {}", websocketId, cpId);

            if (!chargePointRegistryService.registerSession(cpId, websocketId)) {
                jsonServer.closeSession(websocketId);
                return;
            }

            TriggerMessageRequest bootNotification = new TriggerMessageRequest(TriggerMessageRequestType.BootNotification);
            TriggerMessageRequest statusNotificationRequest = new TriggerMessageRequest(TriggerMessageRequestType.StatusNotification);
            jsonServer.send(websocketId, bootNotification);
            jsonServer.send(websocketId, statusNotificationRequest);
        } catch (Exception e) {
            log.error("Error occurred while handling new session, error message: {}", e.getLocalizedMessage());
        }
    }

    /*
    Once the charge point goes offline, the lostSession method gets called.
     */
    @Override
    public void lostSession(UUID sessionIndex) {
        log.info("LostSession event for sessionIndex -> {}", sessionIndex);
        chargePointRegistryService.markSessionClosed(sessionIndex);
    }
}
