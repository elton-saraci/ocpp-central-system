package com.ocppcentralsystem.service;

import com.ocppcentralsystem.exception.ResourceNotFoundException;
import com.ocppcentralsystem.exception.TagNotAuthorizedException;
import com.ocppcentralsystem.model.ChargeTransaction;
import com.ocppcentralsystem.model.ChargeTransactionDTO;
import com.ocppcentralsystem.model.ChargeTransactionRequest;
import com.ocppcentralsystem.model.Tag;
import com.ocppcentralsystem.model.TagStatus;
import com.ocppcentralsystem.model.TagType;
import com.ocppcentralsystem.repository.ChargePointRepository;
import com.ocppcentralsystem.repository.ChargeTransactionRepository;
import com.ocppcentralsystem.repository.TagRepository;
import com.ocppcentralsystem.support.ChargePointFixtures;
import com.ocppcentralsystem.support.TestTenants;
import com.ocppcentralsystem.util.MeterValuesUtility;
import eu.chargetime.ocpp.JSONServer;
import eu.chargetime.ocpp.feature.profile.ServerCoreEventHandler;
import eu.chargetime.ocpp.model.Confirmation;
import eu.chargetime.ocpp.model.Request;
import eu.chargetime.ocpp.model.core.AuthorizeConfirmation;
import eu.chargetime.ocpp.model.core.AuthorizeRequest;
import eu.chargetime.ocpp.model.core.AuthorizationStatus;
import eu.chargetime.ocpp.model.core.MeterValue;
import eu.chargetime.ocpp.model.core.MeterValuesRequest;
import eu.chargetime.ocpp.model.core.RemoteStartStopStatus;
import eu.chargetime.ocpp.model.core.RemoteStartTransactionConfirmation;
import eu.chargetime.ocpp.model.core.RemoteStartTransactionRequest;
import eu.chargetime.ocpp.model.core.RemoteStopTransactionConfirmation;
import eu.chargetime.ocpp.model.core.SampledValue;
import eu.chargetime.ocpp.model.core.StartTransactionConfirmation;
import eu.chargetime.ocpp.model.core.StartTransactionRequest;
import eu.chargetime.ocpp.model.core.StopTransactionConfirmation;
import eu.chargetime.ocpp.model.core.StopTransactionRequest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the whole transaction lifecycle: the inbound OCPP messages a charge point sends
 * (authorize, start, meter values, stop) and the outbound remote start/stop.
 *
 * <p>The OCPP handlers are plain objects, so they can be driven directly. The transport is
 * replaced with a mock - {@link JSONServer} is only used to send requests out and to open the
 * listening socket, and mocking it also keeps the test from binding port 8080.</p>
 */
@SpringBootTest
@Transactional
class ChargeTransactionIntegrationTest {

    private static final String CP_ID = "CP-TX-1";
    private static final String TENANT = TestTenants.DEFAULT;

    @Autowired
    private ChargeTransactionService chargeTransactionService;
    @Autowired
    private ChargePointRepository chargePointRepository;
    @Autowired
    private ChargeTransactionRepository chargeTransactionRepository;
    @Autowired
    private TagRepository tagRepository;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private ServerCoreEventHandler coreEventHandler;

    @MockitoBean
    private JSONServer jsonServer;

    private UUID websocketId;

    @Test
    void authorizeMapsTheTagStateOntoTheOcppStatus() {
        registerChargePoint();
        tagRepository.save(tag("TX-ACTIVE", TagStatus.ACTIVE, LocalDateTime.now().plusDays(1)));
        tagRepository.save(tag("TX-BLOCKED", TagStatus.BLOCKED, LocalDateTime.now().plusDays(1)));
        tagRepository.save(tag("TX-EXPIRED", TagStatus.ACTIVE, LocalDateTime.now().minusDays(1)));

        assertEquals(AuthorizationStatus.Accepted, authorize("TX-ACTIVE").getIdTagInfo().getStatus());
        assertEquals(AuthorizationStatus.Blocked, authorize("TX-BLOCKED").getIdTagInfo().getStatus());
        assertEquals(AuthorizationStatus.Expired, authorize("TX-EXPIRED").getIdTagInfo().getStatus());
        assertEquals(AuthorizationStatus.Invalid, authorize("TX-UNKNOWN").getIdTagInfo().getStatus());

        // The expiry date travels back to the charge point so it knows when to re-authorize.
        assertNotNull(authorize("TX-ACTIVE").getIdTagInfo().getExpiryDate());
    }

    @Test
    void authorizeFromASessionWithoutARegisteredStationCannotAcceptATag() {
        // The tenant of a session comes from the station behind it, so an unknown session has no
        // tenant to look the tag up in.
        assertEquals(AuthorizationStatus.Invalid,
                coreEventHandler.handleAuthorizeRequest(UUID.randomUUID(), new AuthorizeRequest("TX-ACTIVE"))
                        .getIdTagInfo().getStatus());
    }

    @Test
    void chargePointInitiatedTransactionRunsThroughItsFullLifecycle() {
        registerChargePoint();
        tagRepository.save(tag("TX-TAG", TagStatus.ACTIVE, null));

        StartTransactionConfirmation started = coreEventHandler.handleStartTransactionRequest(websocketId,
                new StartTransactionRequest(1, "TX-TAG", 50, ZonedDateTime.now()));

        assertEquals(AuthorizationStatus.Accepted, started.getIdTagInfo().getStatus());
        int transactionId = started.getTransactionId();
        assertTrue(transactionId > 0);

        ChargeTransaction created = reloadTransaction(transactionId);
        assertEquals(CP_ID, created.getChargePoint().getCpId());
        assertEquals(1, created.getConnectorId());
        assertEquals("TX-TAG", created.getIdTag());
        assertTrue(created.isActive());

        // Meter values arrive in kWh and must be stored in Wh.
        MeterValuesRequest meterValues = new MeterValuesRequest(1);
        meterValues.setTransactionId(transactionId);
        meterValues.setMeterValue(new MeterValue[]{
                meterValue("100")
        });
        coreEventHandler.handleMeterValuesRequest(websocketId, meterValues);

        assertEquals(100000, reloadTransaction(transactionId).getLatestMeterValue());

        StopTransactionConfirmation stopped = coreEventHandler.handleStopTransactionRequest(websocketId,
                stopRequest(transactionId, 120000));

        assertEquals(AuthorizationStatus.Accepted, stopped.getIdTagInfo().getStatus());

        ChargeTransaction finished = reloadTransaction(transactionId);
        assertEquals(120000, finished.getMeterStop());
        assertEquals(120000, finished.getLatestMeterValue());
        assertFalse(finished.isActive());
    }

    @Test
    void meterValuesForAnUnknownTransactionDoNotBreakTheSession() {
        registerChargePoint();
        MeterValuesRequest meterValues = new MeterValuesRequest(1);
        meterValues.setTransactionId(9999);
        meterValues.setMeterValue(new MeterValue[]{
                meterValue("5")
        });

        assertNotNull(coreEventHandler.handleMeterValuesRequest(websocketId, meterValues));
    }

    @Test
    void startTransactionIsRejectedForUnknownBlockedAndExpiredTags() {
        registerChargePoint();
        tagRepository.save(tag("TX-BLOCKED", TagStatus.BLOCKED, LocalDateTime.now().plusDays(1)));
        tagRepository.save(tag("TX-EXPIRED", TagStatus.ACTIVE, LocalDateTime.now().minusDays(1)));

        StartTransactionConfirmation unknown = start("TX-NOWHERE");
        assertEquals(AuthorizationStatus.Invalid, unknown.getIdTagInfo().getStatus());
        assertEquals(0, unknown.getTransactionId());

        assertEquals(AuthorizationStatus.Blocked, start("TX-BLOCKED").getIdTagInfo().getStatus());
        assertEquals(0, start("TX-BLOCKED").getTransactionId());

        assertEquals(AuthorizationStatus.Expired, start("TX-EXPIRED").getIdTagInfo().getStatus());

        // A rejected start must not leave a transaction behind.
        assertTrue(chargeTransactionRepository.findByTenantAndTag_IdTagOrderByLastUpdatedDesc(TENANT, "TX-BLOCKED").isEmpty());
        assertTrue(chargeTransactionRepository.findByTenantAndTag_IdTagOrderByLastUpdatedDesc(TENANT, "TX-EXPIRED").isEmpty());
    }

    @Test
    void repeatStartReusesTheRunningTransactionInsteadOfCreatingAnother() {
        registerChargePoint();
        tagRepository.save(tag("TX-TAG", TagStatus.ACTIVE, null));

        int firstId = coreEventHandler.handleStartTransactionRequest(websocketId,
                new StartTransactionRequest(1, "TX-TAG", 50, ZonedDateTime.now())).getTransactionId();

        StartTransactionConfirmation second = coreEventHandler.handleStartTransactionRequest(websocketId,
                new StartTransactionRequest(2, "TX-TAG", 80, ZonedDateTime.now()));

        assertEquals(firstId, second.getTransactionId());
        assertEquals(1, chargeTransactionRepository
                .findByTenantAndTag_IdTagOrderByLastUpdatedDesc(TENANT, "TX-TAG").size());

        ChargeTransaction reloaded = reloadTransaction(firstId);
        assertEquals(2, reloaded.getConnectorId());
        assertEquals(80, reloaded.getMeterStart());
    }

    @Test
    void stopTransactionForAnUnknownIdIsRejected() {
        registerChargePoint();

        StopTransactionConfirmation confirmation = coreEventHandler.handleStopTransactionRequest(websocketId,
                stopRequest(4242, 10));

        assertEquals(AuthorizationStatus.Invalid, confirmation.getIdTagInfo().getStatus());
    }

    @Test
    void remoteStartStoresAnActiveTransactionWhenTheChargePointAccepts() throws Exception {
        registerChargePoint();
        tagRepository.save(tag("TX-TAG", TagStatus.ACTIVE, null));
        answerWith(_ -> new RemoteStartTransactionConfirmation(RemoteStartStopStatus.Accepted));

        ChargeTransactionDTO transaction = chargeTransactionService.startChargeTransaction(
                TENANT, new ChargeTransactionRequest(CP_ID, 1, "TX-TAG"));

        assertNotNull(transaction);
        assertTrue(transaction.isActive());
        assertEquals("TX-TAG", transaction.getIdTag());
        assertEquals(CP_ID, transaction.getCpId());
        verify(jsonServer).send(eq(websocketId), any(RemoteStartTransactionRequest.class));
    }

    @Test
    void remoteStartIsStoredInactiveWhenTheChargePointRejects() throws Exception {
        registerChargePoint();
        tagRepository.save(tag("TX-TAG", TagStatus.ACTIVE, null));
        answerWith(_ -> new RemoteStartTransactionConfirmation(RemoteStartStopStatus.Rejected));

        ChargeTransactionDTO transaction = chargeTransactionService.startChargeTransaction(
                TENANT, new ChargeTransactionRequest(CP_ID, 1, "TX-TAG"));

        assertNotNull(transaction);
        assertFalse(transaction.isActive());
    }

    @Test
    void remoteStartIsRejectedWithoutContactingTheChargePointWhenInputsAreWrong() throws Exception {
        registerChargePoint();
        tagRepository.save(tag("TX-BLOCKED", TagStatus.BLOCKED, LocalDateTime.now().plusDays(1)));

        // Unknown charge point, unknown tag and blocked tag are all rejected up front.
        ResourceNotFoundException unknownChargePoint = assertThrows(ResourceNotFoundException.class,
                () -> chargeTransactionService.startChargeTransaction(TENANT,
                        new ChargeTransactionRequest("CP-GHOST", 1, "TX-TAG")));
        assertEquals("CHARGE_POINT_NOT_FOUND", unknownChargePoint.getCode());

        ResourceNotFoundException unknownTag = assertThrows(ResourceNotFoundException.class,
                () -> chargeTransactionService.startChargeTransaction(TENANT,
                        new ChargeTransactionRequest(CP_ID, 1, "TX-NOWHERE")));
        assertEquals("TAG_NOT_FOUND", unknownTag.getCode());

        TagNotAuthorizedException blocked = assertThrows(TagNotAuthorizedException.class,
                () -> chargeTransactionService.startChargeTransaction(TENANT,
                        new ChargeTransactionRequest(CP_ID, 1, "TX-BLOCKED")));
        assertEquals("TAG_NOT_AUTHORIZED", blocked.getCode());

        verify(jsonServer, never()).send(any(UUID.class), any(Request.class));
    }

    @Test
    void remoteStopRejectsAnUnknownTransactionAndReportsWhatTheChargePointAnswered() throws Exception {
        ResourceNotFoundException notFound = assertThrows(ResourceNotFoundException.class,
                () -> chargeTransactionService.stopChargeTransaction(TENANT, 4242));
        assertEquals("CHARGE_TRANSACTION_NOT_FOUND", notFound.getCode());

        registerChargePoint();
        tagRepository.save(tag("TX-TAG", TagStatus.ACTIVE, null));
        int transactionId = start("TX-TAG").getTransactionId();

        answerWith(_ -> new RemoteStopTransactionConfirmation(RemoteStartStopStatus.Accepted));

        assertTrue(chargeTransactionService.stopChargeTransaction(TENANT, transactionId));
    }

    @Test
    void storedTransactionsCanBeQueriedThroughTheService() {
        registerChargePoint();
        tagRepository.save(tag("TX-TAG", TagStatus.ACTIVE, null));
        int transactionId = start("TX-TAG").getTransactionId();

        assertEquals("TX-TAG", chargeTransactionService.findChargeTransactionById(TENANT, transactionId).getIdTag());
        assertEquals(1, chargeTransactionService.findAllChargingTransactions(TENANT).size());

        ResourceNotFoundException notFound = assertThrows(ResourceNotFoundException.class,
                () -> chargeTransactionService.findChargeTransactionById(TENANT, 4242));
        assertEquals("CHARGE_TRANSACTION_NOT_FOUND", notFound.getCode());
    }

    private void registerChargePoint() {
        websocketId = UUID.randomUUID();
        chargePointRepository.save(ChargePointFixtures.connectedStation(CP_ID, websocketId));
    }

    private StartTransactionConfirmation start(String idTag) {
        return coreEventHandler.handleStartTransactionRequest(websocketId,
                new StartTransactionRequest(1, idTag, 0, ZonedDateTime.now()));
    }

    private AuthorizeConfirmation authorize(String idTag) {
        // Over the session of the registered station: that is where the tenant comes from.
        return coreEventHandler.handleAuthorizeRequest(websocketId, new AuthorizeRequest(idTag));
    }

    /**
     * The transaction handlers write with bulk JPQL updates that bypass the persistence context,
     * so the session has to be flushed and cleared before reading the stored row back.
     */
    private ChargeTransaction reloadTransaction(int transactionId) {
        entityManager.flush();
        entityManager.clear();
        return chargeTransactionRepository.findById(transactionId).orElseThrow();
    }

    /**
     * Built with setters on purpose: the library's positional constructor is
     * {@code (meterStop, timestamp, transactionId)}, which is not the order the fields appear in
     * the OCPP message, so passing arguments through positionally silently swaps them.
     */
    private static StopTransactionRequest stopRequest(int transactionId, int meterStop) {
        StopTransactionRequest request = new StopTransactionRequest();
        request.setTransactionId(transactionId);
        request.setMeterStop(meterStop);
        request.setTimestamp(ZonedDateTime.now());
        return request;
    }

    private Tag tag(String idTag, TagStatus status, LocalDateTime expiryDate) {
        return Tag.builder()
                .tenant(TENANT)
                .idTag(idTag)
                .customerName("Customer " + idTag)
                .tagType(TagType.RFID)
                .status(status)
                .expiryDate(expiryDate)
                .build();
    }

    private MeterValue meterValue(String value) {
        SampledValue sampledValue = new SampledValue(value);
        sampledValue.setMeasurand(MeterValuesUtility.ENERGY_KEYWORD);
        sampledValue.setUnit(MeterValuesUtility.KWH_UNIT);
        return new MeterValue(ZonedDateTime.now(), new SampledValue[]{sampledValue});
    }

    private void answerWith(Function<Request, Confirmation> confirmationFor) throws Exception {
        when(jsonServer.send(any(UUID.class), any(Request.class))).thenAnswer(invocation -> {
            Request request = invocation.getArgument(1);
            Confirmation confirmation = confirmationFor.apply(request);
            if (confirmation == null) {
                // An AssertionError propagates instead of being swallowed by the service's catch block.
                throw new AssertionError("No confirmation stubbed for " + request.getClass().getSimpleName());
            }
            return CompletableFuture.completedFuture(confirmation);
        });
    }
}
