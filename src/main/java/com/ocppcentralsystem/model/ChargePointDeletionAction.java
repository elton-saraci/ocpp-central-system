package com.ocppcentralsystem.model;

/**
 * Outcome of a request to delete a station.
 */
public enum ChargePointDeletionAction {

    /** The station had no transactions and was removed. */
    DELETED,
    /** The station has transaction history and was disabled instead. */
    DISABLED
}
