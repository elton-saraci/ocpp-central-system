package com.ocppcentralsystem.support;

import com.ocppcentralsystem.model.ChargePoint;
import com.ocppcentralsystem.model.Connector;
import com.ocppcentralsystem.model.ConnectorFormat;
import com.ocppcentralsystem.model.ConnectorType;
import com.ocppcentralsystem.model.PowerType;
import com.ocppcentralsystem.model.WebsocketConnectionStatus;
import eu.chargetime.ocpp.model.core.ChargePointStatus;

import java.util.UUID;

/**
 * Builds the station rows the OCPP handlers expect to find: registered, enabled and holding an
 * open session, exactly as {@code ChargePointRegistryService.registerSession} leaves them.
 */
public final class ChargePointFixtures {

    private ChargePointFixtures() {
    }

    public static ChargePoint connectedStation(String cpId) {
        return connectedStation(cpId, UUID.randomUUID());
    }

    public static ChargePoint connectedStation(String cpId, UUID websocketId) {
        return ChargePoint.builder()
                .cpId(cpId)
                .websocketId(websocketId)
                .connectionStatus(WebsocketConnectionStatus.OPEN)
                .enabled(true)
                .build();
    }

    /** A station with the given connector numbers registered, as an operator would configure it. */
    public static ChargePoint stationWithConnectors(String cpId, int... connectorIds) {
        ChargePoint station = connectedStation(cpId);
        for (int connectorId : connectorIds) {
            station.addConnector(connector(connectorId));
        }
        return station;
    }

    /** A three phase type 2 socket: what most AC stations offer on every connector. */
    public static Connector connector(int connectorId) {
        return Connector.builder()
                .connectorId(connectorId)
                .type(ConnectorType.IEC_62196_T2)
                .format(ConnectorFormat.SOCKET)
                .powerType(PowerType.AC_3_PHASE)
                .maxVoltage(230)
                .maxAmperage(16)
                .maxElectricPower(11000)
                .status(ChargePointStatus.Available)
                .build();
    }
}
