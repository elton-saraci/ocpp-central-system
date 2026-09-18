package com.ocppcentralsystem.controller;

import com.ocppcentralsystem.model.ChargePoint;
import com.ocppcentralsystem.model.ChargeTransaction;
import com.ocppcentralsystem.model.Tag;
import com.ocppcentralsystem.repository.ChargeTransactionRepository;
import com.ocppcentralsystem.repository.TagRepository;
import com.ocppcentralsystem.mcp.ChargePointMcpTools;
import com.ocppcentralsystem.service.ChargePointRegistryService;
import com.ocppcentralsystem.tenant.TenantResolver;
import eu.chargetime.ocpp.JSONServer;
import eu.chargetime.ocpp.feature.profile.ServerCoreEventHandler;
import eu.chargetime.ocpp.model.core.AuthorizationStatus;
import eu.chargetime.ocpp.model.core.AuthorizeRequest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pins what "separated per tenant" means: a caller only sees and only writes rows of its own
 * tenant, an absent header falls back to the default tenant, and an OCPP session acts for the
 * tenant of the station that opened it.
 *
 * <p>This is scoping, not security - there is no authentication yet, so a caller can name any
 * tenant it likes. What is guaranteed here is that naming a tenant cannot reach another one's
 * rows.</p>
 */
@SpringBootTest
@Transactional
@AutoConfigureMockMvc
class TenantIsolationIntegrationTest {

    private static final String ACME = "acme";
    private static final String BETA = "beta";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ChargePointRegistryService registry;
    @Autowired
    private TagRepository tagRepository;
    @Autowired
    private ChargeTransactionRepository chargeTransactionRepository;
    @Autowired
    private ServerCoreEventHandler coreEventHandler;
    @Autowired
    private ChargePointMcpTools chargePointMcpTools;
    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private JSONServer jsonServer;

    @Test
    void aTagIsOnlyVisibleToItsOwnTenant() throws Exception {
        createTag(ACME, "RFID-ACME");

        mockMvc.perform(get("/tag/RFID-ACME").header(TenantResolver.TENANT_HEADER, ACME))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idTag").value("RFID-ACME"));

        mockMvc.perform(get("/tag/RFID-ACME").header(TenantResolver.TENANT_HEADER, BETA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TAG_NOT_FOUND"));

        mockMvc.perform(get("/tag").header(TenantResolver.TENANT_HEADER, ACME))
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(get("/tag").header(TenantResolver.TENANT_HEADER, BETA))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void aCallerWithoutATenantHeaderWorksInTheDefaultTenant() throws Exception {
        createTag(null, "RFID-DEFAULT");

        mockMvc.perform(get("/tag/RFID-DEFAULT").header(TenantResolver.TENANT_HEADER, "default"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/tag/RFID-DEFAULT").header(TenantResolver.TENANT_HEADER, BETA))
                .andExpect(status().isNotFound());
    }

    @Test
    void anIdTagAnotherTenantHoldsIsReportedAsSuchRatherThanFailingOnThePrimaryKey() throws Exception {
        createTag(ACME, "RFID-SHARED");

        // Identifiers are unique across the instance, so the second tenant cannot reuse the idTag.
        mockMvc.perform(post("/tag")
                        .header(TenantResolver.TENANT_HEADER, BETA)
                        .contentType(APPLICATION_JSON)
                        .content("{\"idTag\":\"RFID-SHARED\",\"customerName\":\"Beta\",\"tagType\":\"RFID\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TAG_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.message").value(containsString("another tenant")));
    }

    @Test
    void aStationIsOnlyVisibleToItsOwnTenant() throws Exception {
        registerStation();

        mockMvc.perform(get("/charge-point").header(TenantResolver.TENANT_HEADER, ACME))
                .andExpect(jsonPath("$[0].cpId").value("acme/CP-1"));

        mockMvc.perform(get("/charge-point").header(TenantResolver.TENANT_HEADER, BETA))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void oneTenantCannotOperateAnotherTenantsStation() throws Exception {
        registerStation();

        // Same cpId, different tenant: the station is not theirs, so it does not exist for them.
        mockMvc.perform(post("/charge-point/hard-reset")
                        .header(TenantResolver.TENANT_HEADER, BETA)
                        .param("cpId", "acme/CP-1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CHARGE_POINT_NOT_FOUND"));

        mockMvc.perform(get("/charge-point/power")
                        .header(TenantResolver.TENANT_HEADER, BETA)
                        .param("cpId", "acme/CP-1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CHARGE_POINT_NOT_FOUND"));
    }

    @Test
    void anOcppSessionAuthorizesAgainstTheTenantOfItsStation() throws Exception {
        registerStation();
        createTag(BETA, "RFID-BETA-ONLY");
        createTag(ACME, "RFID-ACME");

        UUID session = UUID.randomUUID();
        registry.registerSession("acme/CP-1", session);

        // The tag exists, but for another tenant, so this station may not use it.
        assertEquals(AuthorizationStatus.Invalid,
                coreEventHandler.handleAuthorizeRequest(session, new AuthorizeRequest("RFID-BETA-ONLY"))
                        .getIdTagInfo().getStatus());

        assertEquals(AuthorizationStatus.Accepted,
                coreEventHandler.handleAuthorizeRequest(session, new AuthorizeRequest("RFID-ACME"))
                        .getIdTagInfo().getStatus());
    }

    @Test
    void aTransactionIsOnlyVisibleToTheTenantOfItsStation() throws Exception {
        registerStation();
        createTag(ACME, "RFID-ACME");
        int transactionId = storeATransaction();

        mockMvc.perform(get("/charge-transaction/" + transactionId).header(TenantResolver.TENANT_HEADER, ACME))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cpId").value("acme/CP-1"));

        mockMvc.perform(get("/charge-transaction/" + transactionId).header(TenantResolver.TENANT_HEADER, BETA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CHARGE_TRANSACTION_NOT_FOUND"));

        mockMvc.perform(get("/charge-transaction").header(TenantResolver.TENANT_HEADER, BETA))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void theMcpToolsFallBackToTheDefaultTenantWhenThereIsNoRequest() throws Exception {
        registerStation();

        // Called directly there is no request to take a tenant from, so the default tenant applies
        // - and this station is not part of it. The header path is covered by TenantMcpIntegrationTest.
        assertFalse(chargePointMcpTools.ocppListChargePoints().contains("acme/CP-1"));
    }

    private void createTag(String tenant, String idTag) throws Exception {
        var request = post("/tag")
                .contentType(APPLICATION_JSON)
                .content("{\"idTag\":\"" + idTag + "\",\"customerName\":\"Customer " + idTag + "\",\"tagType\":\"RFID\"}");
        if (tenant != null) {
            request = request.header(TenantResolver.TENANT_HEADER, tenant);
        }
        mockMvc.perform(request).andExpect(status().isCreated());
    }

    private void registerStation() throws Exception {
        mockMvc.perform(post("/charge-point")
                        .header(TenantResolver.TENANT_HEADER, TenantIsolationIntegrationTest.ACME)
                        .contentType(APPLICATION_JSON)
                        .content("{\"cpId\":\"" + "acme/CP-1" + "\"}"))
                .andExpect(status().isCreated());
    }

    private int storeATransaction() {
        ChargePoint station = registry.requireStation(ACME, "acme/CP-1");
        Tag tag = tagRepository.findByTenantAndIdTag(ACME, "RFID-ACME").orElseThrow();

        ChargeTransaction transaction = chargeTransactionRepository.save(new ChargeTransaction(station, 1, tag));
        entityManager.flush();
        return transaction.getChargeTransactionId();
    }
}
