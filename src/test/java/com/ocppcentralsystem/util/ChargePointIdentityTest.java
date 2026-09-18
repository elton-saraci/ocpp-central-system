package com.ocppcentralsystem.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The OCPP library reports the whole request path as the session identifier, protocol segment
 * included. These pin how that path becomes the identity the station registry keys on, because
 * getting it wrong makes every station look unregistered.
 */
class ChargePointIdentityTest {

    @Test
    void theProtocolSegmentIsNotPartOfTheIdentity() {
        assertEquals("CP-1", ChargePointIdentity.fromPath("OCPP16/CP-1"));
    }

    @Test
    void theProtocolSegmentIsMatchedWhateverItsCase() {
        assertEquals("CP-1", ChargePointIdentity.fromPath("ocpp16/CP-1"));
    }

    @Test
    void slashesAfterTheProtocolSegmentBelongToTheIdentity() {
        assertEquals("acme/server1/CP-1", ChargePointIdentity.fromPath("OCPP16/acme/server1/CP-1"));
    }

    @Test
    void aLeadingSlashIsIgnored() {
        assertEquals("acme/CP-1", ChargePointIdentity.fromPath("/acme/CP-1"));
    }

    @Test
    void aStationThatConnectedWithoutTheProtocolSegmentKeepsItsWholePath() {
        assertEquals("CP-1", ChargePointIdentity.fromPath("CP-1"));
        assertEquals("acme/CP-1", ChargePointIdentity.fromPath("acme/CP-1"));
    }

    @Test
    void theOtherCommonProtocolSpellingIsAlsoDropped() {
        assertEquals("CP-1", ChargePointIdentity.fromPath("OCPP1.6/CP-1"));
        assertEquals("acme/CP-1", ChargePointIdentity.fromPath("ocpp1.6/acme/CP-1"));
    }

    @Test
    void aQueryStringIsNotPartOfTheIdentity() {
        // Charge points append things like ?token=... - the identity is what comes before it.
        assertEquals("CP-1", ChargePointIdentity.fromPath("OCPP16/CP-1?token=abc"));
        assertEquals("CP-1", ChargePointIdentity.fromPath("CP-1?token=abc"));
        assertEquals("acme/CP-1", ChargePointIdentity.fromPath("/acme/CP-1?vendor=x"));
    }

    @Test
    void noIdentifierStaysNoIdentifier() {
        assertNull(ChargePointIdentity.fromPath(null));
    }
}
