package com.ocppcentralsystem.model;

/**
 * Outcome of checking whether a tag may be used.
 *
 * <p>This is the single place where the rules "blocked beats expired" and
 * "unknown tags are rejected" live, so the OCPP handlers and the REST layer
 * cannot drift apart.</p>
 */
public enum TagAuthorization {
    ACCEPTED,
    BLOCKED,
    EXPIRED,
    /** No tag with that identifier exists in the database. */
    UNKNOWN;

    public static TagAuthorization of(Tag tag) {
        if (tag == null) {
            return UNKNOWN;
        }
        if (TagStatus.BLOCKED.equals(tag.getStatus())) {
            return BLOCKED;
        }
        if (tag.isExpired()) {
            return EXPIRED;
        }
        return ACCEPTED;
    }

    public boolean isAccepted() {
        return this == ACCEPTED;
    }
}
