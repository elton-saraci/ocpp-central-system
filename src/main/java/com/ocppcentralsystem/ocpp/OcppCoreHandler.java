package com.ocppcentralsystem.ocpp;

import com.ocppcentralsystem.factory.ConfirmationFactory;
import com.ocppcentralsystem.model.ChargePoint;
import com.ocppcentralsystem.model.Tag;
import com.ocppcentralsystem.model.TagAuthorization;
import com.ocppcentralsystem.service.ChargePointRegistryService;
import com.ocppcentralsystem.service.ChargeTransactionService;
import com.ocppcentralsystem.service.TagService;
import com.ocppcentralsystem.util.MeterValuesUtility;
import eu.chargetime.ocpp.feature.profile.ServerCoreEventHandler;
import eu.chargetime.ocpp.model.core.*;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;

/**
 * Handles the OCPP 1.6 core requests a charge point sends while its session is up: authorization,
 * boot, status, heartbeat, meter values, and the transaction lifecycle.
 *
 * <p>Every handler resolves the tenant from the session first, so a station can only ever read or
 * write the data of the tenant that owns it. The handlers stay thin on purpose - decision making
 * and persistence live in the services they call.
 */
@Component
@Slf4j
@AllArgsConstructor
public class OcppCoreHandler implements ServerCoreEventHandler {

    private final TagService tagService;
    private final ChargeTransactionService chargeTransactionService;
    private final ChargePointRegistryService chargePointRegistryService;

    private static final String TIMEZONE_ID = "UTC";
    private static final int DEFAULT_INTERVAL = 60;
    private static final int INVALID_TRANSACTION_ID = 0;

    @Override
    public AuthorizeConfirmation handleAuthorizeRequest(UUID sessionIndex, AuthorizeRequest request) {
        log.info("Incoming authorization request sessionIndex -> {},  authorizeRequest tag -> {}", sessionIndex, request.getIdTag());
        // Scoped to the tenant of the station behind the session: a tag registered by one
        // tenant never authorizes another tenant's station.
        Tag tag = tenantOf(sessionIndex)
                .flatMap(tenant -> tagService.findEntityByIdTag(tenant, request.getIdTag()))
                .orElse(null);
        TagAuthorization authorization = TagAuthorization.of(tag);
        if (!authorization.isAccepted()) {
            log.warn("Rejecting idTag {} for sessionIndex {} -> {}", request.getIdTag(), sessionIndex, authorization);
        }
        return new AuthorizeConfirmation(toIdTagInfo(authorization, tag));
    }

    @Override
    public BootNotificationConfirmation handleBootNotificationRequest(UUID sessionIndex, BootNotificationRequest request) {
        log.info("Boot notification request -> {}, sessionIndex -> {} ", request, sessionIndex);
        ZonedDateTime zonedDateTime = ZonedDateTime.parse(ZonedDateTime.now(ZoneId.of(TIMEZONE_ID)).format(DateTimeFormatter.ISO_INSTANT));

        // A station that is not in the registry, or was disabled, gets Rejected and must not charge.
        Optional<Integer> heartbeatInterval = chargePointRegistryService.recordBootNotification(sessionIndex, request);
        return heartbeatInterval.map(integer -> new BootNotificationConfirmation(zonedDateTime, integer, RegistrationStatus.Accepted))
                .orElseGet(() -> new BootNotificationConfirmation(zonedDateTime, DEFAULT_INTERVAL, RegistrationStatus.Rejected));
    }

    @Override
    public DataTransferConfirmation handleDataTransferRequest(UUID sessionIndex, DataTransferRequest request) {
        log.info("DataTransferRequest -> {}, sessionIndex -> {}", request, sessionIndex);

        //write here the logic that handles DataTransferRequests according to your CP central system requirements.

        return new DataTransferConfirmation(DataTransferStatus.Accepted);
    }

    @Override
    public HeartbeatConfirmation handleHeartbeatRequest(UUID sessionIndex, HeartbeatRequest request) {
        log.info("Heartbeat request -> {}, sessionIndex -> {}", request, sessionIndex);
        chargePointRegistryService.recordHeartbeat(sessionIndex);
        return new HeartbeatConfirmation(ZonedDateTime.now());
    }

    @Override
    public MeterValuesConfirmation handleMeterValuesRequest(UUID sessionIndex, MeterValuesRequest request) {
        log.info("MeterValues request -> {}, sessionIndex - {}", request, sessionIndex);

        Integer currentMeterValue = MeterValuesUtility.fetchEnergyValue(request);
        Integer currentPowerValue = MeterValuesUtility.fetchPowerValue(request);

        if (currentMeterValue == null) {
            log.warn("Skipping update as meter value is null for chargeTransactionId {}", request.getTransactionId());
            return new MeterValuesConfirmation();
        }

        Optional<String> tenant = tenantOf(sessionIndex);
        if (tenant.isEmpty()) {
            log.warn("Skipping meter values for sessionIndex {} as no station holds it", sessionIndex);
            return new MeterValuesConfirmation();
        }

        boolean recorded = chargeTransactionService.recordMeterValues(
                tenant.get(),
                request.getTransactionId(),
                currentMeterValue,
                currentPowerValue,
                request.getConnectorId()
        );

        if (!recorded) {
            log.warn("No transaction was found with id: {}", request.getTransactionId());
        }

        return new MeterValuesConfirmation();
    }

    @Override
    public StartTransactionConfirmation handleStartTransactionRequest(UUID websocketId,
                                                                      StartTransactionRequest request) {
        log.info("StartTransactionRequest -> {}, sessionIndex -> {}", request, websocketId);
        Optional<Integer> runningTransaction = chargeTransactionService
                .continueRunningTransaction(websocketId, request);
        if (runningTransaction.isPresent()) {
            return new StartTransactionConfirmation(
                    new IdTagInfo(AuthorizationStatus.Accepted), runningTransaction.get());
        }

        ChargePoint chargePoint = chargePointRegistryService.requireStationForSession(websocketId);
        Tag tag = tagService.findEntityByIdTag(chargePoint.getTenant(), request.getIdTag()).orElse(null);
        TagAuthorization authorization = TagAuthorization.of(tag);
        if (!authorization.isAccepted()) {
            log.warn("Refusing StartTransaction for idTag {} on sessionIndex {} -> {}",
                    request.getIdTag(), websocketId, authorization);
            return new StartTransactionConfirmation(toIdTagInfo(authorization, tag), INVALID_TRANSACTION_ID);
        }

        int transactionId = chargeTransactionService.recordStartedTransaction(chargePoint, tag, request);
        return new StartTransactionConfirmation(new IdTagInfo(AuthorizationStatus.Accepted), transactionId);
    }

    @Override
    public StatusNotificationConfirmation handleStatusNotificationRequest(UUID sessionIndex,
                                                                          StatusNotificationRequest request) {
        log.info("Received StatusNotificationRequest: {}, sessionIndex: {}", request, sessionIndex);
        chargePointRegistryService.recordConnectorStatus(sessionIndex, request);
        return new StatusNotificationConfirmation();
    }

    @Override
    public StopTransactionConfirmation handleStopTransactionRequest(UUID sessionIndex,
                                                                    StopTransactionRequest request) {
        log.info("StopTransactionRequest -> {}, sessionIndex -> {}", request, sessionIndex);
        boolean stopped = tenantOf(sessionIndex)
                .map(tenant -> chargeTransactionService.recordStop(tenant, request.getTransactionId(),
                        request.getMeterStop()))
                .orElse(false);
        if (!stopped) {
            log.warn("No charge transaction found on database for id {}", request.getTransactionId());
            return ConfirmationFactory.generateStopTransactionConfirmation(AuthorizationStatus.Invalid);
        }
        return ConfirmationFactory.generateStopTransactionConfirmation(AuthorizationStatus.Accepted);
    }

    /**
     * @return the tenant the session acts for, or empty when no registered station holds it. A
     *         station that got past the connection gate always resolves one.
     */
    private Optional<String> tenantOf(UUID websocketId) {
        return chargePointRegistryService.findSessionTenant(websocketId);
    }

    /** Maps the tag decision onto the OCPP status the charge point understands. */
    private static AuthorizationStatus toOcppAuthorizationStatus(TagAuthorization authorization) {
        return switch (authorization) {
            case ACCEPTED -> AuthorizationStatus.Accepted;
            case BLOCKED -> AuthorizationStatus.Blocked;
            case EXPIRED -> AuthorizationStatus.Expired;
            case UNKNOWN -> AuthorizationStatus.Invalid;
        };
    }

    private static IdTagInfo toIdTagInfo(TagAuthorization authorization, Tag tag) {
        IdTagInfo idTagInfo = new IdTagInfo(toOcppAuthorizationStatus(authorization));
        if (tag != null && tag.getExpiryDate() != null) {
            idTagInfo.setExpiryDate(tag.getExpiryDate().atZone(ZoneId.of(TIMEZONE_ID)));
        }
        return idTagInfo;
    }
}
