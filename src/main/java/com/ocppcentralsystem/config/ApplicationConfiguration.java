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
     * <p>Along with {@link #smartChargingPhases} and {@link #smartChargingVoltage} this describes
     * the installed hardware: the power API takes watts, while charge points expect amperes, so a
     * limit is converted with {@code P = phases x voltage x I}. That is why these are configuration
     * rather than constants - a single-phase site only has to say so, through the environment
     * ({@code SMARTCHARGING_PHASES=1}) or a flag ({@code --smartcharging.phases=1}).</p>
     *
     * <p>All three are optional: the defaults sit in the placeholders, so nothing has to be
     * declared to run. They describe a three-phase 230 V charger, where 11 kW is roughly
     * 15.9 A.</p>
     */
    @Value("${smartcharging.stack-level:1}")
    private int smartChargingStackLevel;

    /** 1 for single-phase chargers, 3 for three-phase ones. */
    @Value("${smartcharging.phases:3}")
    private int smartChargingPhases;

    /** Nominal phase voltage. */
    @Value("${smartcharging.voltage:230}")
    private int smartChargingVoltage;
}
