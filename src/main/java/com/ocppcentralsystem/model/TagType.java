package com.ocppcentralsystem.model;

/**
 * The kind of credential the driver uses to authorize a charging session.
 */
public enum TagType {
    /** Physical RFID card or key fob handed to the customer. */
    RFID,
    /** Virtual tag generated inside a mobile app. */
    APP,
    /** Tag used by an operator to start a session from the central system. */
    REMOTE
}
