package com.ocppcentralsystem.factory;

import eu.chargetime.ocpp.model.core.AuthorizationStatus;
import eu.chargetime.ocpp.model.core.IdTagInfo;
import eu.chargetime.ocpp.model.core.StopTransactionConfirmation;

public class ConfirmationFactory {

    public static StopTransactionConfirmation generateStopTransactionConfirmation(AuthorizationStatus authorizationStatus) {
        IdTagInfo idTagInfo = new IdTagInfo(authorizationStatus);
        StopTransactionConfirmation stopTransactionConfirmation = new StopTransactionConfirmation();
        stopTransactionConfirmation.setIdTagInfo(idTagInfo);
        return stopTransactionConfirmation;
    }

}
