package com.ocppcentralsystem.model;

/**
 * Outcome of a delete request for a tag.
 */
public enum TagDeletionAction {
    /** The tag had no transaction history and was removed. */
    DELETED,
    /** The tag has transaction history and was blocked instead of removed. */
    BLOCKED
}
