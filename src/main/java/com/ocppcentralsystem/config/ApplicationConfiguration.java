package com.ocppcentralsystem.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
@Data
public class ApplicationConfiguration {

    @Value("${websocket.port}")
    private int websocketPort;

    /**
     * Host to bind the OCPP WebSocket server to. {@code 0.0.0.0} makes it reachable from outside
     * the container, which is required when running in Docker.
     */
    @Value("${websocket.host:0.0.0.0}")
    private String websocketHost;

    /**
     * Which profile wins when something else also limits the same connector.
     *
     * <p>The power API takes watts while charge points expect amperes, so a limit is converted with
     * {@code P = phases x voltage x I}. That conversion reads the installation from the connector
     * being limited - its
     * {@link com.ocppcentralsystem.model.Connector#getPowerType() powerType} and
     * {@code maxVoltage}. {@link #smartChargingPhases} and {@link #smartChargingVoltage} are the
     * fallback for what a connector does not describe: one registered without a power type, or a
     * limit that covers the whole station.</p>
     *
     * <p>All three are optional: the defaults sit in the placeholders, so nothing has to be
     * declared to run. They describe a three-phase 230 V charger, where 11 kW is roughly
     * 15.9 A.</p>
     */
    @Value("${smartcharging.stack-level:1}")
    private int smartChargingStackLevel;

    /** Fallback phase count: 1 for single-phase chargers, 3 for three-phase ones. */
    @Value("${smartcharging.phases:3}")
    private int smartChargingPhases;

    /** Fallback nominal phase voltage. */
    @Value("${smartcharging.voltage:230}")
    private int smartChargingVoltage;
}
