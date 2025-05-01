package com.ocppcentralsystem.factory;


import com.ocppcentralsystem.model.ChargePoint;
import com.ocppcentralsystem.model.ChargeTransaction;
import eu.chargetime.ocpp.model.core.StartTransactionRequest;

import java.time.LocalDateTime;

public class ChargeTransactionFactory {

    public static ChargeTransaction updateChargeTransactionBasedOnRequest(ChargeTransaction chargeTransaction,
                                                                          StartTransactionRequest startTransactionRequest) {
        chargeTransaction.setConnectorId(startTransactionRequest.getConnectorId());
        chargeTransaction.setMeterStart(startTransactionRequest.getMeterStart());
        chargeTransaction.setLastUpdated(LocalDateTime.now());
        return chargeTransaction;
    }

    public static ChargeTransaction createNewChargingTransactionFromStart(StartTransactionRequest startTransactionRequest, ChargePoint chargePoint) {
        return new ChargeTransaction(chargePoint, startTransactionRequest.getConnectorId(), startTransactionRequest.getIdTag());
    }

}
