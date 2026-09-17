package com.ocppcentralsystem.controller;

import com.ocppcentralsystem.exception.ResourceNotFoundException;
import com.ocppcentralsystem.model.ChargePoint;
import com.ocppcentralsystem.model.WebsocketConnectionStatus;
import com.ocppcentralsystem.repository.ChargePointRepository;
import com.ocppcentralsystem.service.ChargePointService;
import eu.chargetime.ocpp.JSONServer;
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
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
 * <p>{@link JSONServer} is mocked: the remote operations only make sense against a connected
 * charge point, and mocking the transport avoids binding port 8080 in the test JVM.</p>
 */
@SpringBootTest
@Transactional
@AutoConfigureMockMvc
class ChargePointIntegrationTest {

    private static final String CP_ID = "CP-1";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ChargePointService chargePointService;
    @Autowired
    private ChargePointRepository chargePointRepository;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private ServerCoreEventHandler coreEventHandler;

    @MockitoBean
    private JSONServer jsonServer;

    @Test
    void chargePointIsFoundByItsWebsocketIdAndCanBeUpdated() {
        ChargePoint chargePoint = save();
        UUID websocketId = chargePoint.getWebsocketId();

        assertEquals(CP_ID, chargePointRepository.findByWebsocketId(websocketId).orElseThrow().getCpId());
        assertTrue(chargePointRepository.findByWebsocketId(UUID.randomUUID()).isEmpty());

        LocalDateTime heartbeatArrived = LocalDateTime.now().plusMinutes(5);
        assertEquals(1, chargePointRepository.updateLastUpdatedByWebsocketId(websocketId, heartbeatArrived));
        assertEquals(0, chargePointRepository.updateLastUpdatedByWebsocketId(UUID.randomUUID(), heartbeatArrived));

        chargePointRepository.updateConnectionStatusByWebsocketId(websocketId, WebsocketConnectionStatus.CLOSED);

        ChargePoint reloaded = reload();
        assertTrue(reloaded.getLastUpdated().isAfter(chargePoint.getLastUpdated()));
        assertEquals(WebsocketConnectionStatus.CLOSED, reloaded.getWebsocketConnectionStatus());
    }

    @Test
    void heartbeatAndStatusNotificationUpdateTheChargePoint() {
        ChargePoint chargePoint = save();
        UUID websocketId = chargePoint.getWebsocketId();
        LocalDateTime registeredAt = chargePoint.getLastUpdated();

        coreEventHandler.handleStatusNotificationRequest(websocketId,
                new StatusNotificationRequest(1, ChargePointErrorCode.NoError, ChargePointStatus.Charging));

        ChargePoint afterStatusNotification = reload();
        assertEquals(ChargePointStatus.Charging, afterStatusNotification.getConnectors().get(1));
        assertTrue(afterStatusNotification.getLastUpdated().isAfter(registeredAt));

        coreEventHandler.handleHeartbeatRequest(websocketId, new HeartbeatRequest());

        assertTrue(reload().getLastUpdated().isAfter(afterStatusNotification.getLastUpdated()));
    }

    @Test
    void statusNotificationFromAnUnknownSessionIsIgnored() {
        save();

        assertNotNull(coreEventHandler.handleStatusNotificationRequest(UUID.randomUUID(),
                new StatusNotificationRequest(1, ChargePointErrorCode.NoError, ChargePointStatus.Available)));
    }

    @Test
    void bootNotificationIsAccepted() {
        BootNotificationConfirmation confirmation = coreEventHandler.handleBootNotificationRequest(UUID.randomUUID(),
                new BootNotificationRequest("Vendor", "Model"));

        assertEquals(RegistrationStatus.Accepted, confirmation.getStatus());
    }

    @Test
    void remoteResetReportsWhatTheChargePointAnswered() throws Exception {
        save();
        answerWith(new ResetConfirmation(ResetStatus.Accepted));

        assertTrue(chargePointService.sendResetRequestToChargePoint(ResetType.Hard, CP_ID));
    }

    @Test
    void remoteOperationsRejectAnUnknownChargePoint() throws Exception {
        ResourceNotFoundException notFound = assertThrows(ResourceNotFoundException.class,
                () -> chargePointService.sendResetRequestToChargePoint(ResetType.Hard, "CP-GHOST"));
        assertEquals("CHARGE_POINT_NOT_FOUND", notFound.getCode());

        assertThrows(ResourceNotFoundException.class,
                () -> chargePointService.sendConnectorUnlockToChargePoint("CP-GHOST", 1));
        assertThrows(ResourceNotFoundException.class,
                () -> chargePointService.sendTriggerMessageRequestToChargePoint("CP-GHOST", 1, TriggerMessageRequestType.Heartbeat));
        assertThrows(ResourceNotFoundException.class,
                () -> chargePointService.sendChangeConfigurationRequestToChargePoint("CP-GHOST", "key", "value"));

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

        assertTrue(chargePointService.sendConnectorUnlockToChargePoint(CP_ID, 1));
        assertTrue(chargePointService.sendTriggerMessageRequestToChargePoint(CP_ID, 1,
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
        return chargePointRepository.save(new ChargePoint(cpId, UUID.randomUUID(), new HashMap<>(),
                WebsocketConnectionStatus.OPEN, LocalDateTime.now()));
    }

    private ChargePoint reload() {
        entityManager.flush();
        entityManager.clear();
        return chargePointRepository.findById(CP_ID).orElseThrow();
    }

    private void answerWith(Confirmation confirmation) throws Exception {
        when(jsonServer.send(any(UUID.class), any(Request.class))).thenReturn(completed(confirmation));
    }

    private static CompletableFuture<Confirmation> completed(Confirmation confirmation) {
        return CompletableFuture.completedFuture(confirmation);
    }
}
