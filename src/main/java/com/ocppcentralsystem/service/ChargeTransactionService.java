package com.ocppcentralsystem.service;

import com.ocppcentralsystem.config.ApplicationConfiguration;
import com.ocppcentralsystem.mapper.ChargeTransactionMapper;
import com.ocppcentralsystem.model.ChargePoint;
import com.ocppcentralsystem.model.ChargeTransaction;
import com.ocppcentralsystem.model.ChargeTransactionDTO;
import com.ocppcentralsystem.model.ChargeTransactionRequest;
import com.ocppcentralsystem.repository.ChargePointRepository;
import com.ocppcentralsystem.repository.ChargeTransactionRepository;
import eu.chargetime.ocpp.JSONServer;
import eu.chargetime.ocpp.model.Confirmation;
import eu.chargetime.ocpp.model.core.*;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

import static eu.chargetime.ocpp.model.core.RemoteStartStopStatus.Rejected;

@Service
@AllArgsConstructor
@Slf4j
public class ChargeTransactionService {

    private final ChargeTransactionRepository chargeTransactionRepository;
    private final ChargeTransactionMapper mapper;
    private final ChargePointRepository chargePointRepository;
    private final JSONServer jsonServer;
    private final ApplicationConfiguration applicationConfiguration;

    public ChargeTransactionDTO startChargeTransaction(ChargeTransactionRequest chargeTransactionRequest) {
        try {

            Optional<ChargePoint> optionalChargePoint = chargePointRepository.findById(chargeTransactionRequest.getCpId());

            if (optionalChargePoint.isEmpty()) {
                log.error("Charge Point does not exist: {}", chargeTransactionRequest.getCpId());
                throw new RuntimeException("Charge Point does not exist: " + chargeTransactionRequest.getCpId());
            }

            if (!applicationConfiguration.getWhitelistedIdTags().contains(chargeTransactionRequest.getIdTag())) {
                log.error("Invalid tagId {}, not part of the whitelisted tags {}", chargeTransactionRequest.getIdTag(), applicationConfiguration.getWhitelistedIdTags());
                throw new RuntimeException("Invalid idTag: " + chargeTransactionRequest.getIdTag());
            }

            RemoteStartTransactionRequest request = new RemoteStartTransactionRequest(chargeTransactionRequest.getIdTag());
            request.setConnectorId(chargeTransactionRequest.getConnectorId());
            Confirmation confirmation = jsonServer.send(optionalChargePoint.get().getWebsocketId(), request).toCompletableFuture().get();
            RemoteStartTransactionConfirmation remoteStartTransactionConfirmation = (RemoteStartTransactionConfirmation) confirmation;

            log.info("The RemoteStartTransactionConfirmation response: {}", remoteStartTransactionConfirmation);
            ChargeTransaction chargeTransaction = saveChargeTransaction(optionalChargePoint.get(), request.getConnectorId(),
                    request.getIdTag(), remoteStartTransactionConfirmation.getStatus());
            return mapper.toDto(chargeTransaction);
        } catch (Exception ex) {
            log.error("Error occurred, error message: {}", ex.getLocalizedMessage());
            return null;
        }
    }

    private ChargeTransaction saveChargeTransaction(ChargePoint chargePoint, int connectorId, String idTag, RemoteStartStopStatus confirmation) {
        ChargeTransaction chargeTransaction = new ChargeTransaction(chargePoint, connectorId, idTag);
        if(confirmation.equals(Rejected)) {
            chargeTransaction.setActive(false);
        }
        return chargeTransactionRepository.save(chargeTransaction);
    }

    public boolean stopChargeTransaction(int chargeTransactionId) {
        try {
            Optional<ChargeTransaction> optionalChargeTransaction = chargeTransactionRepository.findById(chargeTransactionId);

            if(optionalChargeTransaction.isEmpty()) {
                log.error("Charge transaction does not exist for id {}", chargeTransactionId);
                return false;
            }

            RemoteStopTransactionRequest request = new RemoteStopTransactionRequest(chargeTransactionId);
            Confirmation confirmation = jsonServer.send(optionalChargeTransaction.get().getChargePoint().getWebsocketId(), request).toCompletableFuture().get();
            RemoteStopTransactionConfirmation remoteStopTransactionConfirmation = (RemoteStopTransactionConfirmation) confirmation;
            log.info("RemoteStopTransactionConfirmation -> {}", remoteStopTransactionConfirmation.getStatus());

            return remoteStopTransactionConfirmation.getStatus().equals(RemoteStartStopStatus.Accepted);
        } catch (Exception ex) {
            log.error("Error occurred while stopping transaction, error message: {}", ex.getLocalizedMessage());
            return false;
        }
    }

    public ChargeTransactionDTO findChargeTransactionById(int transactionId) {
        return mapper.toDto(chargeTransactionRepository.findById(transactionId).orElse(null));
    }

    public List<ChargeTransactionDTO> findAllChargingTransactions() {
        return mapper.toDtoList(chargeTransactionRepository.findAll());
    }

}
