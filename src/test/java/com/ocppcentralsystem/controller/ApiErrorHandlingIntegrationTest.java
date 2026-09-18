package com.ocppcentralsystem.controller;

import com.ocppcentralsystem.model.Tag;
import com.ocppcentralsystem.model.TagStatus;
import com.ocppcentralsystem.model.TagType;
import com.ocppcentralsystem.repository.ChargePointRepository;
import com.ocppcentralsystem.repository.TagRepository;
import com.ocppcentralsystem.support.ChargePointFixtures;
import eu.chargetime.ocpp.JSONServer;
import eu.chargetime.ocpp.model.Request;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pins down the error contract: every failing request answers with the same
 * {@code ApiErrorResponse} envelope, a status that says who is at fault, and a stable code.
 *
 * <p>Deliberately uses the same annotations as {@link ChargePointIntegrationTest} so both classes
 * share one Spring context.</p>
 */
@SpringBootTest
@Transactional
@AutoConfigureMockMvc
class ApiErrorHandlingIntegrationTest {

    private static final String CP_ID = "CP-ERR-1";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ChargePointRepository chargePointRepository;
    @Autowired
    private TagRepository tagRepository;

    @MockitoBean
    private JSONServer jsonServer;

    @Test
    void unknownTagIsReportedWithItsCode() throws Exception {
        mockMvc.perform(get("/tag/RFID-GHOST"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("TAG_NOT_FOUND"))
                .andExpect(jsonPath("$.path").value("/tag/RFID-GHOST"))
                .andExpect(jsonPath("$.message").value(containsString("RFID-GHOST")));
    }

    @Test
    void duplicateTagIsReportedAsAConflict() throws Exception {
        String body = "{\"idTag\":\"RFID-DUP\",\"customerName\":\"Jane Doe\",\"tagType\":\"RFID\"}";

        mockMvc.perform(post("/tag").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/tag").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TAG_ALREADY_EXISTS"));
    }

    @Test
    void invalidBodyIsReportedWithTheOffendingFields() throws Exception {
        mockMvc.perform(post("/tag").contentType(APPLICATION_JSON)
                        .content("{\"idTag\":\"RFID-X\",\"customerName\":\"\",\"tagType\":\"RFID\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[0]").value(containsString("customerName")));
    }

    @Test
    void missingParameterIsReportedWithItsName() throws Exception {
        mockMvc.perform(post("/charge-point/hard-reset"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_PARAMETER"))
                .andExpect(jsonPath("$.details[0]").value("cpId is required"));
    }

    @Test
    void invalidParameterIsReported() throws Exception {
        mockMvc.perform(get("/tag").param("tagType", "NOPE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.message").value("tagType is not valid"))
                .andExpect(jsonPath("$.details[0]").value(containsString("TagType")));
    }

    @Test
    void unknownChargePointIsReported() throws Exception {
        mockMvc.perform(post("/charge-point/hard-reset").param("cpId", "CP-GHOST"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CHARGE_POINT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value(containsString("CP-GHOST")));
    }

    @Test
    void aChargePointThatDoesNotAnswerIsReportedAsBadGateway() throws Exception {
        registerChargePoint();
        when(jsonServer.send(any(UUID.class), any(Request.class)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("connection lost")));

        mockMvc.perform(post("/charge-point/hard-reset").param("cpId", CP_ID))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("CHARGE_POINT_UNREACHABLE"))
                .andExpect(jsonPath("$.message").value(containsString("connection lost")));
    }

    @Test
    void blockedTagCannotStartACharge() throws Exception {
        registerChargePoint();
        tagRepository.save(Tag.builder()
                .idTag("RFID-BLOCKED")
                .customerName("Blocked Customer")
                .tagType(TagType.RFID)
                .status(TagStatus.BLOCKED)
                .build());

        mockMvc.perform(post("/charge-transaction/start").contentType(APPLICATION_JSON)
                        .content("{\"cpId\":\"" + CP_ID + "\",\"connectorId\":1,\"idTag\":\"RFID-BLOCKED\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TAG_NOT_AUTHORIZED"))
                .andExpect(jsonPath("$.message").value(containsString("BLOCKED")));
    }

    @Test
    void unknownTransactionIsReported() throws Exception {
        mockMvc.perform(get("/charge-transaction/4242"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CHARGE_TRANSACTION_NOT_FOUND"));
    }

    @Test
    void unmappedRoutesAnswerNotFoundRatherThanAServerError() throws Exception {
        mockMvc.perform(post("/charge-point/" + CP_ID + "/hard-reset"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    private void registerChargePoint() {
        chargePointRepository.save(ChargePointFixtures.connectedStation(CP_ID));
    }
}
