package com.ocppcentralsystem.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the two entity conventions the package documentation explains: {@code toString} stays inside
 * the row it describes, and {@code equals} is identity rather than value.
 */
class ChargePointTest {

    private static final String CP_ID = "CP-1";
    private static final String TENANT = "acme";

    @Test
    void toStringNamesTheStationWithoutWalkingTheGraph() {
        ChargePoint station = stationWithAConnector();

        String text = station.toString();

        assertTrue(text.contains(CP_ID), text);
        assertTrue(text.contains(TENANT), text);
        // The connector points back at the station, so a generated toString would recurse forever.
        assertFalse(text.contains("Connector"), text);
    }

    @Test
    void equalityIsIdentityRatherThanValue() {
        ChargePoint one = station();
        ChargePoint sameRow = station();

        // Deliberate: rows are compared within one session and the collections here are lists, so a
        // value comparison would buy nothing and would misbehave on Hibernate proxies.
        assertNotEquals(one, sameRow);
    }

    private static ChargePoint stationWithAConnector() {
        ChargePoint station = station();
        station.addConnector(Connector.builder().connectorId(1).build());
        return station;
    }

    private static ChargePoint station() {
        return ChargePoint.builder().cpId(CP_ID).tenant(TENANT).build();
    }
}
