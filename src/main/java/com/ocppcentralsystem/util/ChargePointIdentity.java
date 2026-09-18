package com.ocppcentralsystem.util;

import java.util.Locale;
import java.util.Set;

/**
 * Turns the path a station connected to into the charge point identity the registry keys on.
 *
 * <p>An OCPP 1.6 URL may carry the protocol as its first path segment - {@code wss://host/OCPP16/CP-1}
 * - and that segment is part of the URL, not of the station's identity. Everything after it is the
 * identity, and the identity may itself contain slashes, so an operator can namespace stations
 * under it, e.g. {@code acme/server1/CP-1}.</p>
 *
 * <p>The protocol segment is optional: {@code wss://host/CP-1} identifies itself as {@code CP-1} just
 * as well, and anything from the query string on is dropped, so a station that appends one is still
 * recognized.</p>
 */
public final class ChargePointIdentity {

    /**
     * Protocol segments an OCPP 1.6 URL may carry before the identity. Both spellings seen in the
     * wild are listed; anything else stays part of the identity rather than being guessed at.
     */
    private static final Set<String> PROTOCOL_SEGMENTS = Set.of("ocpp16", "ocpp1.6");

    private ChargePointIdentity() {
    }

    /**
     * @param path the identifier the OCPP library reports, e.g. {@code OCPP16/acme/server1/CP-1}
     * @return the identity with the protocol segment and any query string removed, e.g.
     *         {@code acme/server1/CP-1}
     */
    public static String fromPath(String path) {
        if (path == null) {
            return null;
        }

        String identity = path.startsWith("/") ? path.substring(1) : path;

        // The library hands over the whole request target, so a station that appends a query string
        // - a token, a vendor parameter - would otherwise be looked up with it still attached.
        int queryStart = identity.indexOf('?');
        if (queryStart >= 0) {
            identity = identity.substring(0, queryStart);
        }

        int protocolEnd = identity.indexOf('/');
        if (protocolEnd < 0) {
            return identity;
        }

        String firstSegment = identity.substring(0, protocolEnd).toLowerCase(Locale.ROOT);
        return PROTOCOL_SEGMENTS.contains(firstSegment) ? identity.substring(protocolEnd + 1) : identity;
    }
}
