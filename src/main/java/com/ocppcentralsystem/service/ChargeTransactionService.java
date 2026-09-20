package com.ocppcentralsystem.service;

import com.ocppcentralsystem.exception.ResourceNotFoundException;
import com.ocppcentralsystem.exception.TagNotAuthorizedException;
import com.ocppcentralsystem.factory.ChargeTransactionFactory;
import com.ocppcentralsystem.mapper.ChargeTransactionMapper;
import com.ocppcentralsystem.model.ChargePoint;
import com.ocppcentralsystem.model.ChargeTransaction;
import com.ocppcentralsystem.model.ChargeTransactionDTO;
import com.ocppcentralsystem.model.ChargeTransactionRequest;
import com.ocppcentralsystem.model.Tag;
import com.ocppcentralsystem.repository.ChargePointRepository;
import com.ocppcentralsystem.repository.ChargeTransactionRepository;
import eu.chargetime.ocpp.model.core.*;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static eu.chargetime.ocpp.model.core.RemoteStartStopStatus.Rejected;

@Service
@AllArgsConstructor
@Slf4j
public class ChargeTransactionService {

    private final ChargeTransactionRepository chargeTransactionRepository;
    private final ChargeTransactionMapper mapper;
    private final ChargePointRepository chargePointRepository;
    private final TagService tagService;
    private final ChargePointCommunicator chargePointCommunicator;

    public ChargeTransactionDTO startChargeTransaction(String tenant, ChargeTransactionRequest chargeTransactionRequest) {
        ChargePoint chargePoint = chargePointRepository.findByTenantAndCpId(tenant, chargeTransactionRequest.getCpId())
                .orElseThrow(() -> new ResourceNotFoundException("Charge point", chargeTransactionRequest.getCpId()));

        Tag tag = tagService.findEntityByIdTag(tenant, chargeTransactionRequest.getIdTag())
                .orElseThrow(() -> new ResourceNotFoundException("Tag", chargeTransactionRequest.getIdTag()));
        if (!tag.isUsable()) {
            throw new TagNotAuthorizedException(tag);
        }

        RemoteStartTransactionRequest request = new RemoteStartTransactionRequest(tag.getIdTag());
        request.setConnectorId(chargeTransactionRequest.getConnectorId());

        RemoteStartTransactionConfirmation remoteStartTransactionConfirmation = chargePointCommunicator.send(
                chargePoint.getWebsocketId(), request, RemoteStartTransactionConfirmation.class);
        log.info("The RemoteStartTransactionConfirmation response: {}", remoteStartTransactionConfirmation);

        ChargeTransaction chargeTransaction = saveChargeTransaction(chargePoint, request.getConnectorId(),
                tag, remoteStartTransactionConfirmation.getStatus());
        return mapper.toDto(chargeTransaction);
    }

    private ChargeTransaction saveChargeTransaction(ChargePoint chargePoint, int connectorId, Tag tag, RemoteStartStopStatus confirmation) {
        ChargeTransaction chargeTransaction = new ChargeTransaction(chargePoint, connectorId, tag);
        if(Rejected.equals(confirmation)) {
            chargeTransaction.setActive(false);
        }
        return chargeTransactionRepository.save(chargeTransaction);
    }

    /**
     * Continues the transaction this session is already running for the tag, for a station that
     * repeats its StartTransaction instead of opening a second one.
     *
     * @return the transaction id, or empty when the session has nothing running for that tag.
     */
    @Transactional
    public Optional<Integer> continueRunningTransaction(UUID websocketId, StartTransactionRequest request) {
        return chargeTransactionRepository.findLatestByWebsocketIdAndIdTag(websocketId, request.getIdTag())
                .map(transaction -> {
                    ChargeTransaction updated = ChargeTransactionFactory
                            .updateChargeTransactionBasedOnRequest(transaction, request);
                    log.info("Updated ChargeTransaction {} with connectorId {} and meterStart {}",
                            updated.getChargeTransactionId(), request.getConnectorId(), request.getMeterStart());
                    return chargeTransactionRepository.save(updated).getChargeTransactionId();
                });
    }

    /** Stores a transaction the station started itself and returns the id it was given. */
    @Transactional
    public int recordStartedTransaction(ChargePoint chargePoint, Tag tag, StartTransactionRequest request) {
        ChargeTransaction chargeTransaction = ChargeTransactionFactory
                .createNewChargingTransactionFromStart(request, chargePoint, tag);
        int transactionId = chargeTransactionRepository.save(chargeTransaction).getChargeTransactionId();
        log.info("Created ChargeTransaction {} for idTag {}", transactionId, request.getIdTag());
        return transactionId;
    }

    /**
     * Records the values a station reported for a transaction it is running. The entity is loaded and
     * changed rather than updated in bulk, so {@code lastUpdated} is stamped by the entity itself.
     *
     * @return {@code false} when this tenant has no transaction with that id.
     */
    @Transactional
    public boolean recordMeterValues(String tenant, int transactionId, Integer meterValue, Integer powerValue,
                                     Integer connectorId) {
        return chargeTransactionRepository.findByTenantAndChargeTransactionId(tenant, transactionId)
                .map(transaction -> {
                    transaction.setLatestMeterValue(meterValue);
                    transaction.setLatestPowerValue(powerValue);
                    transaction.setConnectorId(connectorId);
                    chargeTransactionRepository.save(transaction);
                    return true;
                })
                .orElse(false);
    }

    /**
     * Marks a transaction as stopped with the reading the station sent.
     *
     * @return {@code false} when this tenant has no transaction with that id.
     */
    @Transactional
    public boolean recordStop(String tenant, int transactionId, Integer meterStop) {
        return chargeTransactionRepository.findByTenantAndChargeTransactionId(tenant, transactionId)
                .map(transaction -> {
                    transaction.setMeterStop(meterStop);
                    transaction.setLatestMeterValue(meterStop);
                    transaction.setActive(false);
                    chargeTransactionRepository.save(transaction);
                    return true;
                })
                .orElse(false);
    }

    /**
     * @return {@code true} when the charge point accepted the stop. A charge point that answers
     *         {@code Rejected} is not an error - the caller asked, the charge point said no.
     */
    public boolean stopChargeTransaction(String tenant, int chargeTransactionId) {
        // Scoped to the tenant first, so a transaction of another tenant cannot be stopped by
        // guessing its id.
        chargeTransactionRepository.findByTenantAndChargeTransactionId(tenant, chargeTransactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Charge transaction", chargeTransactionId));

        UUID websocketId = chargeTransactionRepository.findChargePointWebsocketId(chargeTransactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Charge transaction", chargeTransactionId));

        RemoteStopTransactionConfirmation confirmation = chargePointCommunicator.send(
                websocketId, new RemoteStopTransactionRequest(chargeTransactionId), RemoteStopTransactionConfirmation.class);
        log.info("RemoteStopTransactionConfirmation -> {}", confirmation.getStatus());

        return RemoteStartStopStatus.Accepted.equals(confirmation.getStatus());
    }

    @Transactional(readOnly = true)
    public ChargeTransactionDTO findChargeTransactionById(String tenant, int transactionId) {
        return chargeTransactionRepository.findByTenantAndChargeTransactionId(tenant, transactionId)
                .map(mapper::toDto)
                .orElseThrow(() -> new ResourceNotFoundException("Charge transaction", transactionId));
    }

    @Transactional(readOnly = true)
    public List<ChargeTransactionDTO> findAllChargingTransactions(String tenant) {
        return mapper.toDtoList(chargeTransactionRepository.findByTenant(tenant));
    }
}
