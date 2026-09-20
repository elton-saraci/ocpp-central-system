package com.ocppcentralsystem.controller;

import com.ocppcentralsystem.exception.ResourceNotFoundException;
import com.ocppcentralsystem.model.ChargePoint;
import com.ocppcentralsystem.model.WebsocketConnectionStatus;
import com.ocppcentralsystem.repository.ChargePointRepository;
import com.ocppcentralsystem.service.ChargePointRegistryService;
import com.ocppcentralsystem.service.ChargePointService;
import com.ocppcentralsystem.support.AbstractMockMvcIntegrationTest;
import com.ocppcentralsystem.support.ChargePointFixtures;
import eu.chargetime.ocpp.feature.profile.ServerCoreEventHandler;
import eu.chargetime.ocpp.model.Confirmation;
import eu.chargetime.ocpp.model.Request;
import eu.chargetime.ocpp.model.core.BootNotificationConfirmation;
import eu.chargetime.ocpp.model.core.BootNotificationRequest;
import eu.chargetime.ocpp.model.core.ChargePointErrorCode;
import eu.chargetime.ocpp.model.core.ChargePointStatus;
import eu.chargetime.ocpp.model.core.HeartbeatRequest;
import eu.chargetime.ocpp.model.core.RegistrationStatus;
import eu.chargetime.ocpp.model.core.ResetConfirmation;
import eu.chargetime.ocpp.model.core.ResetRequest;
import eu.chargetime.ocpp.model.core.ResetStatus;
import eu.chargetime.ocpp.model.core.ResetType;
import eu.chargetime.ocpp.model.core.StatusNotificationRequest;
import eu.chargetime.ocpp.model.core.UnlockConnectorConfirmation;
import eu.chargetime.ocpp.model.core.UnlockConnectorRequest;
import eu.chargetime.ocpp.model.core.UnlockStatus;
import eu.chargetime.ocpp.model.remotetrigger.TriggerMessageConfirmation;
import eu.chargetime.ocpp.model.remotetrigger.TriggerMessageRequest;
import eu.chargetime.ocpp.model.remotetrigger.TriggerMessageRequestType;
import eu.chargetime.ocpp.model.remotetrigger.TriggerMessageStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers charge point registration and inbound state updates, the remote operations triggered
 * from the API, and the HTTP contract of the charge point endpoints.
 *
 * <p>The OCPP transport is mocked by {@link AbstractMockMvcIntegrationTest}, so these tests drive
 * the remote operations against a stub instead of a connected charge point.</p>
 */
class ChargePointIntegrationTest extends AbstractMockMvcIntegrationTest {

    private static final String CP_ID = "CP-1";

    @Autowired
    private ChargePointService chargePointService;
    @Autowired
    private ChargePointRegistryService chargePointRegistryService;
    @Autowired
    private ChargePointRepository chargePointRepository;
    @Autowired
    private ServerCoreEventHandler coreEventHandler;

    @Test
    void aSessionIsTrackedUntilTheStationDisconnects() {
        ChargePoint chargePoint = save();
        UUID websocketId = chargePoint.getWebsocketId();

        assertEquals(CP_ID, chargePointRepository.findByWebsocketId(websocketId).orElseThrow().getCpId());
        assertTrue(chargePointRepository.findByWebsocketId(UUID.randomUUID()).isEmpty());
        assertTrue(chargePoint.isConnected());

        LocalDateTime registeredAt = chargePoint.getLastUpdated();
        chargePointRegistryService.recordHeartbeat(websocketId);
        chargePointRegistryService.markSessionClosed(websocketId);

        ChargePoint reloaded = reload();
        assertTrue(reloaded.getLastUpdated().isAfter(registeredAt));
        assertEquals(WebsocketConnectionStatus.CLOSED, reloaded.getConnectionStatus());
        assertNull(reloaded.getWebsocketId(), "a closed session forgets the handle it was opened with");
        assertFalse(reloaded.isConnected());
    }

    @Test
    void heartbeatAndStatusNotificationUpdateTheChargePoint() {
        ChargePoint chargePoint = chargePointRepository.save(ChargePointFixtures.stationWithConnectors(CP_ID, 1));
        UUID websocketId = chargePoint.getWebsocketId();
        LocalDateTime registeredAt = chargePoint.getLastUpdated();

        coreEventHandler.handleStatusNotificationRequest(websocketId,
                new StatusNotificationRequest(1, ChargePointErrorCode.NoError, ChargePointStatus.Charging));

        ChargePoint afterStatusNotification = reload();
        assertEquals(ChargePointStatus.Charging, afterStatusNotification.getConnectors().getFirst().getStatus());
        // Read into a LocalDateTime: the reloaded entity stays managed, so a later write would
        // overwrite the value were it read from the entity again.
        LocalDateTime statusNotificationAt = afterStatusNotification.getLastUpdated();
        assertTrue(statusNotificationAt.isAfter(registeredAt));

        coreEventHandler.handleHeartbeatRequest(websocketId, new HeartbeatRequest());

        LocalDateTime afterHeartbeat = reload().getLastUpdated();
        assertTrue(afterHeartbeat.isAfter(statusNotificationAt),
                () -> "heartbeat at " + afterHeartbeat + " did not move past " + statusNotificationAt);
    }

    @Test
    void statusNotificationFromAnUnknownSessionIsIgnored() {
        save();

        assertNotNull(coreEventHandler.handleStatusNotificationRequest(UUID.randomUUID(),
                new StatusNotificationRequest(1, ChargePointErrorCode.NoError, ChargePointStatus.Available)));
    }

    @Test
    void bootNotificationIsStoredForARegisteredStation() {
        ChargePoint chargePoint = save();

        BootNotificationConfirmation confirmation = coreEventHandler.handleBootNotificationRequest(
                chargePoint.getWebsocketId(), new BootNotificationRequest("Vendor", "Model"));

        assertEquals(RegistrationStatus.Accepted, confirmation.getStatus());
        assertEquals(60, confirmation.getInterval(), "without a configured interval a station heartbeats every minute");

        ChargePoint reloaded = reload();
        assertEquals("Vendor", reloaded.getVendor());
        assertEquals("Model", reloaded.getModel());
    }

    @Test
    void remoteResetReportsWhatTheChargePointAnswered() throws Exception {
        save();
        answerWith(new ResetConfirmation(ResetStatus.Accepted));

        assertTrue(chargePointService.sendResetRequestToChargePoint(TENANT, ResetType.Hard, CP_ID));
    }

    @Test
    void remoteOperationsRejectAnUnknownChargePoint() throws Exception {
        ResourceNotFoundException notFound = assertThrows(ResourceNotFoundException.class,
                () -> chargePointService.sendResetRequestToChargePoint(TENANT, ResetType.Hard, "CP-GHOST"));
        assertEquals("CHARGE_POINT_NOT_FOUND", notFound.getCode());

        assertThrows(ResourceNotFoundException.class,
                () -> chargePointService.sendConnectorUnlockToChargePoint(TENANT, "CP-GHOST", 1));
        assertThrows(ResourceNotFoundException.class,
                () -> chargePointService.sendTriggerMessageRequestToChargePoint(TENANT, "CP-GHOST", 1, TriggerMessageRequestType.Heartbeat));
        assertThrows(ResourceNotFoundException.class,
                () -> chargePointService.sendChangeConfigurationRequestToChargePoint(TENANT, "CP-GHOST", "key", "value"));

        verify(jsonServer, never()).send(any(UUID.class), any(Request.class));
    }

    @Test
    void connectorUnlockAndTriggerMessageReportWhatTheChargePointAnswered() throws Exception {
        save();
        when(jsonServer.send(any(UUID.class), any(Request.class))).thenAnswer(invocation -> {
            Request request = invocation.getArgument(1);
            if (request instanceof UnlockConnectorRequest) {
                return completed(new UnlockConnectorConfirmation(UnlockStatus.Unlocked));
            }
            if (request instanceof TriggerMessageRequest) {
                return completed(new TriggerMessageConfirmation(TriggerMessageStatus.Accepted));
            }
            throw new AssertionError("Unexpected request: " + request.getClass().getSimpleName());
        });

        assertTrue(chargePointService.sendConnectorUnlockToChargePoint(TENANT, CP_ID, 1));
        assertTrue(chargePointService.sendTriggerMessageRequestToChargePoint(TENANT, CP_ID, 1,
                TriggerMessageRequestType.Heartbeat));
    }

    @Test
    void chargePointsAreExposedThroughTheApi() throws Exception {
        save();

        mockMvc.perform(get("/charge-point"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].cpId").value(CP_ID));
    }

    @Test
    void cpIdContainingASlashIsRoutedAndResolved() throws Exception {
        // Regression guard: cpId used to be a path variable, and a slash in it broke routing.
        save("ACME/GARAGE-1");
        answerWith(new ResetConfirmation(ResetStatus.Accepted));

        mockMvc.perform(post("/charge-point/hard-reset").param("cpId", "ACME/GARAGE-1"))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));

        verify(jsonServer).send(any(UUID.class), any(ResetRequest.class));
    }

    @Test
    void cpIdWithASlashSurvivesUrlParsing() throws Exception {
        save("ACME/GARAGE-1");
        answerWith(new ResetConfirmation(ResetStatus.Accepted));

        mockMvc.perform(post("/charge-point/hard-reset?cpId=ACME/GARAGE-1"))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));
    }

    @Test
    void cpIdIsRequired() throws Exception {
        mockMvc.perform(post("/charge-point/hard-reset"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void connectorIdIsRequired() throws Exception {
        mockMvc.perform(post("/charge-point/heartbeat").param("cpId", CP_ID))
                .andExpect(status().isBadRequest());
    }

    @Test
    void theOldPathVariableRoutesAreGone() throws Exception {
        mockMvc.perform(post("/charge-point/" + CP_ID + "/hard-reset"))
                .andExpect(status().isNotFound());
    }

    private ChargePoint save() {
        return save(CP_ID);
    }

    private ChargePoint save(String cpId) {
        return chargePointRepository.save(ChargePointFixtures.connectedStation(cpId));
    }

    private ChargePoint reload() {
        flushAndClear();
        return chargePointRepository.findById(CP_ID).orElseThrow();
    }

    private void answerWith(Confirmation confirmation) throws Exception {
        when(jsonServer.send(any(UUID.class), any(Request.class))).thenReturn(completed(confirmation));
    }

    private static CompletableFuture<Confirmation> completed(Confirmation confirmation) {
        return CompletableFuture.completedFuture(confirmation);
    }
}
