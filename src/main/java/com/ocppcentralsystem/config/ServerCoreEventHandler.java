package com.ocppcentralsystem.config;

import com.ocppcentralsystem.factory.ChargeTransactionFactory;
import com.ocppcentralsystem.factory.ConfirmationFactory;
import com.ocppcentralsystem.model.ChargePoint;
import com.ocppcentralsystem.model.ChargeTransaction;
import com.ocppcentralsystem.repository.ChargePointRepository;
import com.ocppcentralsystem.repository.ChargeTransactionRepository;
import com.ocppcentralsystem.util.MeterValuesUtility;
import eu.chargetime.ocpp.feature.profile.ServerCoreProfile;
import eu.chargetime.ocpp.model.core.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Configuration
@Getter
@Slf4j
@AllArgsConstructor
public class ServerCoreEventHandler {

    private final ApplicationConfiguration applicationConfiguration;
    private final ChargeTransactionRepository chargeTransactionRepository;
    private final ChargePointRepository chargePointRepository;
    private static final String TIMEZONE_ID = "UTC";
    private static final int DEFAULT_INTERVAL = 60;
    private static final int INVALID_TRANSACTION_ID = 0;

    @Bean
    public eu.chargetime.ocpp.feature.profile.ServerCoreEventHandler getCoreEventHandler() {
        return new eu.chargetime.ocpp.feature.profile.ServerCoreEventHandler() {

            @Override
            public AuthorizeConfirmation handleAuthorizeRequest(UUID sessionIndex, AuthorizeRequest request) {
                log.info("Incoming authorization request sessionIndex -> {},  authorizeRequest tag -> {}", sessionIndex, request.getIdTag());
                List<String> whitelistedTags = applicationConfiguration.getWhitelistedIdTags();
                AuthorizationStatus authorizationStatus = whitelistedTags.contains(request.getIdTag()) ? AuthorizationStatus.Accepted : AuthorizationStatus.Invalid;
                IdTagInfo idTagInfo = new IdTagInfo(authorizationStatus);
                return new AuthorizeConfirmation(idTagInfo);
            }

            @Override
            public BootNotificationConfirmation handleBootNotificationRequest(UUID sessionIndex, BootNotificationRequest request) {
                log.info("Boot notification request -> {}, sessionIndex -> {} ", request, sessionIndex);
                ZonedDateTime zonedDateTime = ZonedDateTime.parse(ZonedDateTime.now(ZoneId.of(TIMEZONE_ID)).format(DateTimeFormatter.ISO_INSTANT));

                // write here the logic that takes care of boot notification requests according to your CP central system requirements.

                return new BootNotificationConfirmation(zonedDateTime, DEFAULT_INTERVAL, RegistrationStatus.Accepted);
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
                int updated = chargePointRepository.updateLastUpdatedByWebsocketId(sessionIndex, LocalDateTime.now());
                if (updated == 0) {
                    log.warn("No ChargePoint found for websocketId: {}", sessionIndex);
                }
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

                int updatedRows = chargeTransactionRepository.updateMeterValues(
                        request.getTransactionId(),
                        currentMeterValue,
                        currentPowerValue,
                        request.getConnectorId(),
                        LocalDateTime.now()
                );

                if(updatedRows == 0) {
                    log.warn("No transaction was found with id: {}", request.getTransactionId());
                }

                return new MeterValuesConfirmation();
            }


            @Override
            public StartTransactionConfirmation handleStartTransactionRequest(UUID websocketId,
                                                                              StartTransactionRequest request) {
                log.info("StartTransactionRequest -> {}, sessionIndex -> {}", request, websocketId);
                Optional<ChargeTransaction> optionalChargeTransaction = chargeTransactionRepository
                        .findLatestByWebsocketIdAndIdTag(websocketId, request.getIdTag());
                if (optionalChargeTransaction.isPresent()) {
                    ChargeTransaction chargeTransaction = ChargeTransactionFactory
                            .updateChargeTransactionBasedOnRequest(optionalChargeTransaction.get(), request);
                    chargeTransactionRepository.save(chargeTransaction);
                    log.info("Updated ChargeTransaction {} with connectorId {} and meterStart {}",
                            chargeTransaction.getChargeTransactionId(), request.getConnectorId(), request.getMeterStart());
                    return new StartTransactionConfirmation(new IdTagInfo(AuthorizationStatus.Accepted), chargeTransaction.getChargeTransactionId());
                } else {
                    log.warn("No ChargeTransaction found for websocketId {} and idTag {}, creating new one.", websocketId, request.getIdTag());
                    ChargePoint chargePoint = chargePointRepository.findByWebsocketId(websocketId).orElseThrow(RuntimeException::new);
                    ChargeTransaction chargeTransaction = ChargeTransactionFactory.createNewChargingTransactionFromStart(request, chargePoint);
                    chargeTransactionRepository.save(chargeTransaction);
                    return new StartTransactionConfirmation(new IdTagInfo(AuthorizationStatus.Accepted), chargeTransaction.getChargeTransactionId());
                }
            }

            @Override
            public StatusNotificationConfirmation handleStatusNotificationRequest(UUID sessionIndex,
                                                                                  StatusNotificationRequest request) {
                log.info("Received StatusNotificationRequest: {}, sessionIndex: {}", request, sessionIndex);
                return chargePointRepository.findByWebsocketId(sessionIndex)
                        .map(chargePoint -> updateChargePointStatus(chargePoint, request))
                        .orElseGet(() -> {
                            log.warn("No ChargePoint found for websocketId: {}", sessionIndex);
                            return new StatusNotificationConfirmation();
                        });
            }

            private StatusNotificationConfirmation updateChargePointStatus(ChargePoint chargePoint,
                                                                           StatusNotificationRequest request) {
                Map<Integer, ChargePointStatus> connectors = chargePoint.getConnectors();
                connectors.put(request.getConnectorId(), request.getStatus());
                chargePoint.setLastUpdated(LocalDateTime.now());
                chargePointRepository.save(chargePoint);
                return new StatusNotificationConfirmation();
            }

            @Override
            public StopTransactionConfirmation handleStopTransactionRequest(UUID sessionIndex,
                                                                            StopTransactionRequest request) {
                log.info("StopTransactionRequest -> {}, sessionIndex -> {}", request, sessionIndex);
                int rowsChanged = chargeTransactionRepository.updateStopTransaction(request.getTransactionId(),
                        request.getMeterStop(), LocalDateTime.now(), false);
                if(rowsChanged == 0) {
                    log.warn("No charge transaction found on database for id {}", request.getTransactionId());
                    return ConfirmationFactory.generateStopTransactionConfirmation(AuthorizationStatus.Invalid);
                }
                return ConfirmationFactory.generateStopTransactionConfirmation(AuthorizationStatus.Accepted);
            }
        };
    }

    @Bean
    public ServerCoreProfile createCore(eu.chargetime.ocpp.feature.profile.ServerCoreEventHandler serverCoreEventHandler) {
        return new ServerCoreProfile(serverCoreEventHandler);
    }

}
