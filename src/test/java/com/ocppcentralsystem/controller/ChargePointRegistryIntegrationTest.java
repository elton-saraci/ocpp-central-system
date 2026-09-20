package com.ocppcentralsystem.controller;

import com.ocppcentralsystem.model.ChargePoint;
import com.ocppcentralsystem.model.ChargeTransaction;
import com.ocppcentralsystem.model.Connector;
import com.ocppcentralsystem.model.Tag;
import com.ocppcentralsystem.model.TagRequest;
import com.ocppcentralsystem.model.TagType;
import com.ocppcentralsystem.repository.ChargePointRepository;
import com.ocppcentralsystem.repository.ChargeTransactionRepository;
import com.ocppcentralsystem.repository.TagRepository;
import com.ocppcentralsystem.service.ChargePointRegistryService;
import com.ocppcentralsystem.service.TagService;
import com.ocppcentralsystem.support.AbstractMockMvcIntegrationTest;
import eu.chargetime.ocpp.feature.profile.ServerCoreEventHandler;
import eu.chargetime.ocpp.model.core.BootNotificationConfirmation;
import eu.chargetime.ocpp.model.core.BootNotificationRequest;
import eu.chargetime.ocpp.model.core.ChargePointErrorCode;
import eu.chargetime.ocpp.model.core.ChargePointStatus;
import eu.chargetime.ocpp.model.core.RegistrationStatus;
import eu.chargetime.ocpp.model.core.StatusNotificationRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers the station registry: the CRUD an operator drives, the connectors stored with a station,
 * and the policy that only a registered, enabled station gets a session.
 */
class ChargePointRegistryIntegrationTest extends AbstractMockMvcIntegrationTest {

    private static final String CP_ID = "CP-REG-1";
    private static final String TAG_ID = "REG-TAG";

    @Autowired
    private ChargePointRegistryService registry;
    @Autowired
    private ChargePointRepository chargePointRepository;
    @Autowired
    private ChargeTransactionRepository chargeTransactionRepository;
    @Autowired
    private TagService tagService;
    @Autowired
    private TagRepository tagRepository;
    @Autowired
    private ServerCoreEventHandler coreEventHandler;

    @Test
    void aStationIsRegisteredWithItsConnectorsAndMetadata() throws Exception {
        mockMvc.perform(post("/charge-point")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "cpId": "CP-REG-1",
                                  "name": "Depot 1",
                                  "vendor": "ACME",
                                  "timezone": "Europe/Rome",
                                  "metadata": {"address": "Via Roma 1", "assetTag": "42"},
                                  "connectors": [
                                    {"connectorId": 1, "type": "IEC_62196_T2", "format": "SOCKET",
                                     "powerType": "AC_3_PHASE", "maxVoltage": 230, "maxAmperage": 32,
                                     "maxElectricPower": 22000}
                                  ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cpId").value(CP_ID))
                .andExpect(jsonPath("$.name").value("Depot 1"))
                .andExpect(jsonPath("$.timezone").value("Europe/Rome"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.connected").value(false))
                .andExpect(jsonPath("$.metadata.address").value("Via Roma 1"))
                .andExpect(jsonPath("$.connectors.length()").value(1))
                .andExpect(jsonPath("$.connectors[0].connectorId").value(1))
                .andExpect(jsonPath("$.connectors[0].maxAmperage").value(32))
                .andExpect(jsonPath("$.connectors[0].powerType").value("AC_3_PHASE"));
    }

    @Test
    void aStationIsListedAndCanBeFiltered() throws Exception {
        registerStation();

        mockMvc.perform(get("/charge-point").param("cpId", CP_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].cpId").value(CP_ID));

        mockMvc.perform(get("/charge-point").param("cpId", "CP-GHOST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        registry.setStationEnabled(TENANT, CP_ID, false);

        mockMvc.perform(get("/charge-point").param("enabled", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(get("/charge-point").param("enabled", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void theSameStationCannotBeRegisteredTwice() throws Exception {
        registerStation();

        mockMvc.perform(post("/charge-point")
                        .contentType(APPLICATION_JSON)
                        .content("{\"cpId\": \"CP-REG-1\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CHARGE_POINT_ALREADY_EXISTS"));
    }

    @Test
    void updatingAStationKeepsTheStateOfConnectorsThatStayRegistered() throws Exception {
        mockMvc.perform(post("/charge-point")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"cpId": "CP-REG-1",
                                 "connectors": [{"connectorId": 1, "maxAmperage": 16}, {"connectorId": 2, "maxAmperage": 16}]}
                                """))
                .andExpect(status().isCreated());

        UUID session = UUID.randomUUID();
        assertTrue(registry.registerSession(CP_ID, session));
        coreEventHandler.handleStatusNotificationRequest(session,
                new StatusNotificationRequest(1, ChargePointErrorCode.NoError, ChargePointStatus.Charging));

        mockMvc.perform(put("/charge-point")
                        .param("cpId", CP_ID)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"cpId": "CP-REG-1",
                                 "name": "Depot 1 (renamed)",
                                 "connectors": [{"connectorId": 1, "maxAmperage": 32}, {"connectorId": 3, "maxAmperage": 64}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Depot 1 (renamed)"))
                .andExpect(jsonPath("$.connectors.length()").value(2));

        ChargePoint reloaded = reload();
        assertEquals(List.of(1, 3), reloaded.getConnectors().stream()
                .map(Connector::getConnectorId).sorted().toList());

        Connector kept = reloaded.getConnectors().stream()
                .filter(connector -> connector.getConnectorId() == 1).findFirst().orElseThrow();
        assertEquals(32, kept.getMaxAmperage());
        assertEquals(ChargePointStatus.Charging, kept.getStatus(),
                "a connector that stays registered keeps the last state the station reported");
    }

    @Test
    void aConnectorNumberListedTwiceIsRejected() throws Exception {
        mockMvc.perform(post("/charge-point")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"cpId": "CP-REG-1",
                                 "connectors": [{"connectorId": 1, "maxAmperage": 16}, {"connectorId": 1, "maxAmperage": 32}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.details[0]").value("connectorId 1 is duplicated"));
    }

    @Test
    void anUnknownStationCannotBeUpdatedOrRemoved() throws Exception {
        mockMvc.perform(put("/charge-point")
                        .param("cpId", "CP-GHOST")
                        .contentType(APPLICATION_JSON)
                        .content("{\"cpId\": \"CP-GHOST\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CHARGE_POINT_NOT_FOUND"));

        mockMvc.perform(patch("/charge-point/enabled").param("cpId", "CP-GHOST").param("enabled", "false"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CHARGE_POINT_NOT_FOUND"));

        mockMvc.perform(delete("/charge-point").param("cpId", "CP-GHOST"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CHARGE_POINT_NOT_FOUND"));
    }

    @Test
    void aStationCanBeDisabledAndEnabledAgain() throws Exception {
        registerStation();

        mockMvc.perform(patch("/charge-point/enabled").param("cpId", CP_ID).param("enabled", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        mockMvc.perform(patch("/charge-point/enabled").param("cpId", CP_ID).param("enabled", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    void onlyRegisteredAndEnabledStationsGetASession() throws Exception {
        registerStation();

        assertFalse(registry.registerSession("CP-GHOST", UUID.randomUUID()),
                "a station that is not in the registry is refused");
        assertTrue(registry.registerSession(CP_ID, UUID.randomUUID()));

        registry.setStationEnabled(TENANT, CP_ID, false);
        assertFalse(registry.registerSession(CP_ID, UUID.randomUUID()),
                "a disabled station is refused even though it is registered");
    }

    @Test
    void bootNotificationIsRejectedWhenTheStationIsUnknownOrDisabled() throws Exception {
        BootNotificationRequest request = new BootNotificationRequest("ACME", "Model-X");

        BootNotificationConfirmation unknownSession = coreEventHandler.handleBootNotificationRequest(
                UUID.randomUUID(), request);
        assertEquals(RegistrationStatus.Rejected, unknownSession.getStatus());

        registerStation();
        UUID session = UUID.randomUUID();
        assertTrue(registry.registerSession(CP_ID, session));
        registry.setStationEnabled(TENANT, CP_ID, false);

        BootNotificationConfirmation disabledStation = coreEventHandler.handleBootNotificationRequest(session, request);
        assertEquals(RegistrationStatus.Rejected, disabledStation.getStatus());
    }

    @Test
    void aStatusNotificationForAConnectorThatIsNotRegisteredIsIgnored() throws Exception {
        registerStation();
        UUID session = UUID.randomUUID();
        assertTrue(registry.registerSession(CP_ID, session));

        coreEventHandler.handleStatusNotificationRequest(session,
                new StatusNotificationRequest(5, ChargePointErrorCode.NoError, ChargePointStatus.Charging));

        assertTrue(reload().getConnectors().isEmpty(),
                "a connector the station never had configured is not invented on the fly");
    }

    @Test
    void aStationWithoutHistoryIsDeleted() throws Exception {
        registerStation();

        mockMvc.perform(delete("/charge-point").param("cpId", CP_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("DELETED"))
                .andExpect(jsonPath("$.transactionCount").value(0));

        flushAndClear();
        assertTrue(chargePointRepository.findById(CP_ID).isEmpty());
    }

    @Test
    void aStationWithTransactionsIsDisabledInsteadOfDeleted() throws Exception {
        registerStation();
        storeATransactionOn();

        mockMvc.perform(delete("/charge-point").param("cpId", CP_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("DISABLED"))
                .andExpect(jsonPath("$.transactionCount").value(1))
                .andExpect(jsonPath("$.chargePoint.enabled").value(false));

        flushAndClear();
        ChargePoint kept = chargePointRepository.findById(CP_ID).orElseThrow();
        assertFalse(kept.isEnabled(), "the station is kept so its transactions still have a parent");
    }

    private void registerStation() throws Exception {
        mockMvc.perform(post("/charge-point")
                        .contentType(APPLICATION_JSON)
                        .content("{\"cpId\": \"" + ChargePointRegistryIntegrationTest.CP_ID + "\", \"name\": \"Depot\"}"))
                .andExpect(status().isCreated());
    }

    private void storeATransactionOn() {
        tagService.createTag(TENANT, TagRequest.builder()
                .idTag(TAG_ID)
                .customerName("Transaction owner")
                .tagType(TagType.RFID)
                .build());

        ChargePoint station = chargePointRepository.findById(ChargePointRegistryIntegrationTest.CP_ID).orElseThrow();
        Tag tag = tagRepository.findById(TAG_ID).orElseThrow();
        chargeTransactionRepository.save(new ChargeTransaction(station, 1, tag));
        flushAndClear();
    }

    private ChargePoint reload() {
        flushAndClear();
        return chargePointRepository.findById(CP_ID).orElseThrow();
    }
}
