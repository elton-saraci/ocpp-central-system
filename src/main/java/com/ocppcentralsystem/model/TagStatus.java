package com.ocppcentralsystem.model;

/**
 * Lifecycle state of a tag as stored in the database.
 *
 * <p>Expiry is not part of this enum: it is derived from {@code expiryDate} so that a tag
 * automatically stops being usable without requiring a scheduled job to flip its state.</p>
 */
public enum TagStatus {
    ACTIVE,
    BLOCKED
}
