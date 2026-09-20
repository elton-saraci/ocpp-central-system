package com.ocppcentralsystem.controller;

import com.ocppcentralsystem.mcp.ChargePointMcpTools;
import com.ocppcentralsystem.repository.ChargePointRepository;
import com.ocppcentralsystem.support.ChargePointFixtures;
import eu.chargetime.ocpp.JSONServer;
import eu.chargetime.ocpp.model.Confirmation;
import eu.chargetime.ocpp.model.Request;
import eu.chargetime.ocpp.model.core.ChargingProfile;
import eu.chargetime.ocpp.model.core.ChargingProfileKindType;
import eu.chargetime.ocpp.model.core.ChargingProfilePurposeType;
import eu.chargetime.ocpp.model.core.ChargingRateUnitType;
import eu.chargetime.ocpp.model.core.ChargingSchedule;
import eu.chargetime.ocpp.model.core.ChargingSchedulePeriod;
import eu.chargetime.ocpp.model.smartcharging.ChargingProfileStatus;
import eu.chargetime.ocpp.model.smartcharging.ClearChargingProfileConfirmation;
import eu.chargetime.ocpp.model.smartcharging.ClearChargingProfileRequest;
import eu.chargetime.ocpp.model.smartcharging.ClearChargingProfileStatus;
import eu.chargetime.ocpp.model.smartcharging.GetCompositeScheduleConfirmation;
import eu.chargetime.ocpp.model.smartcharging.GetCompositeScheduleRequest;
import eu.chargetime.ocpp.model.smartcharging.GetCompositeScheduleStatus;
import eu.chargetime.ocpp.model.smartcharging.SetChargingProfileConfirmation;
import eu.chargetime.ocpp.model.smartcharging.SetChargingProfileRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers the client facing power API and, through the captured OCPP requests, that a caller only
 * has to supply watts while the charging profile is assembled correctly behind the scenes.
 *
 * <p>Carries the same annotations as {@link ChargePointIntegrationTest} so both share one Spring
 * context.</p>
 */
@SpringBootTest
@Transactional
@AutoConfigureMockMvc
class SmartChargingIntegrationTest {

    private static final String CP_ID = "CP-POWER-1";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ChargePointRepository chargePointRepository;
    @Autowired
    private ChargePointMcpTools chargePointMcpTools;

    @MockitoBean
    private JSONServer jsonServer;

    @Test
    void setPowerBuildsATxDefaultProfileInAmperes() throws Exception {
        registerChargePoint();
        answerWith(_ -> new SetChargingProfileConfirmation(ChargingProfileStatus.Accepted));

        mockMvc.perform(put("/charge-point/power").param("cpId", CP_ID).param("powerW", "11000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cpId").value(CP_ID))
                .andExpect(jsonPath("$.connectorId").value(0))
                .andExpect(jsonPath("$.requestedPowerW").value(11000))
                .andExpect(jsonPath("$.applied").value(true))
                .andExpect(jsonPath("$.status").value("Accepted"));

        SetChargingProfileRequest sent = capturedRequest(SetChargingProfileRequest.class);
        assertEquals(0, sent.getConnectorId());

        ChargingProfile profile = sent.getCsChargingProfiles();
        assertEquals(ChargingProfilePurposeType.TxDefaultProfile, profile.getChargingProfilePurpose());
        assertEquals(ChargingProfileKindType.Absolute, profile.getChargingProfileKind());
        assertEquals(1, profile.getStackLevel());
        assertNull(profile.getTransactionId(), "a TxDefaultProfile must not be tied to a transaction");

        ChargingSchedule schedule = profile.getChargingSchedule();
        assertEquals(ChargingRateUnitType.A, schedule.getChargingRateUnit());
        assertNull(schedule.getDuration(), "no duration means the limit stays until it is cleared");
        assertEquals(0, schedule.getChargingSchedulePeriod()[0].getStartPeriod());
        assertEquals(15.9, schedule.getChargingSchedulePeriod()[0].getLimit(), 0.01, "11 kW on 3 x 230 V");
    }

    @Test
    void setPowerCanTargetOneConnectorForALimitedTime() throws Exception {
        registerChargePoint();
        answerWith(_ -> new SetChargingProfileConfirmation(ChargingProfileStatus.Accepted));

        mockMvc.perform(put("/charge-point/power")
                        .param("cpId", CP_ID)
                        .param("powerW", "4200")
                        .param("connectorId", "1")
                        .param("durationMinutes", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connectorId").value(1));

        SetChargingProfileRequest sent = capturedRequest(SetChargingProfileRequest.class);
        assertEquals(1, sent.getConnectorId());
        ChargingSchedule schedule = sent.getCsChargingProfiles().getChargingSchedule();
        assertEquals(6.1, schedule.getChargingSchedulePeriod()[0].getLimit(), 0.01);
        // OCPP expects the schedule duration in seconds, not minutes.
        assertEquals(1800, schedule.getDuration());
    }

    @Test
    void setPowerReportsAChargePointThatRefusesTheProfile() throws Exception {
        registerChargePoint();
        answerWith(_ -> new SetChargingProfileConfirmation(ChargingProfileStatus.NotSupported));

        mockMvc.perform(put("/charge-point/power").param("cpId", CP_ID).param("powerW", "5000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applied").value(false))
                .andExpect(jsonPath("$.status").value("NotSupported"));
    }

    @Test
    void setPowerOnAnUnknownChargePointIsReported() throws Exception {
        mockMvc.perform(put("/charge-point/power").param("cpId", "CP-GHOST").param("powerW", "5000"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CHARGE_POINT_NOT_FOUND"));
    }

    @Test
    void aNegativePowerIsRejectedBeforeTouchingTheChargePoint() throws Exception {
        registerChargePoint();

        mockMvc.perform(put("/charge-point/power").param("cpId", CP_ID).param("powerW", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[0]").value("powerW: must be zero or more"));
    }

    @Test
    void clearPowerRemovesOnlyOurOwnProfile() throws Exception {
        registerChargePoint();
        answerWith(_ -> new ClearChargingProfileConfirmation(ClearChargingProfileStatus.Accepted));

        mockMvc.perform(delete("/charge-point/power").param("cpId", CP_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applied").value(true))
                .andExpect(jsonPath("$.requestedPowerW").doesNotExist());

        ClearChargingProfileRequest sent = capturedRequest(ClearChargingProfileRequest.class);
        assertEquals(1, sent.getId());
        assertEquals(0, sent.getConnectorId());
        assertEquals(ChargingProfilePurposeType.TxDefaultProfile, sent.getChargingProfilePurpose());
        assertEquals(1, sent.getStackLevel());
    }

    @Test
    void readingTheLimitConvertsAmperesBackToWatts() throws Exception {
        registerChargePoint();
        answerWith(_ -> compositeSchedule(ChargingRateUnitType.A, 16.0));

        mockMvc.perform(get("/charge-point/power").param("cpId", CP_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unit").value("A"))
                .andExpect(jsonPath("$.status").value("Accepted"))
                .andExpect(jsonPath("$.periods[0].startPeriodSeconds").value(0))
                .andExpect(jsonPath("$.periods[0].limit").value(16.0))
                .andExpect(jsonPath("$.periods[0].powerW").value(11040.0));

        GetCompositeScheduleRequest sent = capturedRequest(GetCompositeScheduleRequest.class);
        assertEquals(0, sent.getConnectorId());
        assertEquals(900, sent.getDuration(), "defaults to a 15 minute window");
    }

    @Test
    void aWattScheduleIsReportedUnchanged() throws Exception {
        registerChargePoint();
        answerWith(_ -> compositeSchedule(ChargingRateUnitType.W, 7000.0));

        mockMvc.perform(get("/charge-point/power").param("cpId", CP_ID).param("durationMinutes", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unit").value("W"))
                .andExpect(jsonPath("$.periods[0].limit").value(7000.0))
                .andExpect(jsonPath("$.periods[0].powerW").value(7000.0));

        assertEquals(300, capturedRequest(GetCompositeScheduleRequest.class).getDuration());
    }

    @Test
    void theMcpToolSetsTheSameLimitAndReportsTheResult() throws Exception {
        registerChargePoint();
        answerWith(_ -> new SetChargingProfileConfirmation(ChargingProfileStatus.Accepted));

        String json = chargePointMcpTools.ocppSetChargePointPower(CP_ID, 11000, null);

        assertTrue(json.contains("\"applied\":true"), json);
        assertTrue(json.contains("\"status\":\"Accepted\""), json);
        assertEquals(0, capturedRequest(SetChargingProfileRequest.class).getConnectorId());
    }

    @Test
    void theMcpToolReportsAnUnknownChargePointInsteadOfThrowing() {
        String json = chargePointMcpTools.ocppSetChargePointPower("CP-GHOST", 5000, 1);

        assertTrue(json.contains("\"success\":false"), json);
        assertTrue(json.contains("Charge point not found"), json);
    }

    private void registerChargePoint() {
        chargePointRepository.save(ChargePointFixtures.connectedStation(CP_ID));
    }

    private static GetCompositeScheduleConfirmation compositeSchedule(ChargingRateUnitType unit, Double limit) {
        ChargingSchedule schedule = new ChargingSchedule(unit,
                new ChargingSchedulePeriod[]{new ChargingSchedulePeriod(0, limit)});
        GetCompositeScheduleConfirmation confirmation = new GetCompositeScheduleConfirmation(GetCompositeScheduleStatus.Accepted);
        confirmation.setChargingSchedule(schedule);
        return confirmation;
    }

    private void answerWith(Function<Request, Confirmation> confirmationFor) throws Exception {
        when(jsonServer.send(any(UUID.class), any(Request.class))).thenAnswer(invocation -> {
            Request request = invocation.getArgument(1);
            Confirmation confirmation = confirmationFor.apply(request);
            if (confirmation == null) {
                throw new AssertionError("No confirmation stubbed for " + request.getClass().getSimpleName());
            }
            return CompletableFuture.completedFuture(confirmation);
        });
    }

    private <T extends Request> T capturedRequest(Class<T> requestType) throws Exception {
        ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
        verify(jsonServer).send(any(UUID.class), captor.capture());
        return requestType.cast(captor.getValue());
    }
}
