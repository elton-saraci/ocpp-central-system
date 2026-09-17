package com.ocppcentralsystem.service;

import com.ocppcentralsystem.exception.ResourceNotFoundException;
import com.ocppcentralsystem.exception.TagNotAuthorizedException;
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

    public ChargeTransactionDTO startChargeTransaction(ChargeTransactionRequest chargeTransactionRequest) {
        ChargePoint chargePoint = chargePointRepository.findById(chargeTransactionRequest.getCpId())
                .orElseThrow(() -> new ResourceNotFoundException("Charge point", chargeTransactionRequest.getCpId()));

        Tag tag = tagService.findEntityByIdTag(chargeTransactionRequest.getIdTag())
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
     * @return {@code true} when the charge point accepted the stop. A charge point that answers
     *         {@code Rejected} is not an error - the caller asked, the charge point said no.
     */
    public boolean stopChargeTransaction(int chargeTransactionId) {
        UUID websocketId = chargeTransactionRepository.findChargePointWebsocketId(chargeTransactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Charge transaction", chargeTransactionId));

        RemoteStopTransactionConfirmation confirmation = chargePointCommunicator.send(
                websocketId, new RemoteStopTransactionRequest(chargeTransactionId), RemoteStopTransactionConfirmation.class);
        log.info("RemoteStopTransactionConfirmation -> {}", confirmation.getStatus());

        return RemoteStartStopStatus.Accepted.equals(confirmation.getStatus());
    }

    @Transactional(readOnly = true)
    public ChargeTransactionDTO findChargeTransactionById(int transactionId) {
        return chargeTransactionRepository.findById(transactionId)
                .map(mapper::toDto)
                .orElseThrow(() -> new ResourceNotFoundException("Charge transaction", transactionId));
    }

    @Transactional(readOnly = true)
    public List<ChargeTransactionDTO> findAllChargingTransactions() {
        return mapper.toDtoList(chargeTransactionRepository.findAll());
    }
}
