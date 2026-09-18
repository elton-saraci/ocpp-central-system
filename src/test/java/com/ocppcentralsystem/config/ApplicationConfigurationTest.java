package com.ocppcentralsystem.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The smart charging settings are optional. Their defaults belong to the placeholders, so nothing
 * has to be declared in application.yml to run, and a deployment that differs only sets what
 * differs.
 */
class ApplicationConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ApplicationConfiguration.class)
            .withPropertyValues("websocket.port=8080");

    @Test
    void defaultsDescribeAThreePhaseCharger() {
        runner.run(context -> {
            ApplicationConfiguration configuration = context.getBean(ApplicationConfiguration.class);

            assertEquals(3, configuration.getSmartChargingPhases());
            assertEquals(230, configuration.getSmartChargingVoltage());
            assertEquals(1, configuration.getSmartChargingStackLevel());
        });
    }

    @Test
    void aSinglePhaseSiteOnlyHasToSetThePhaseCount() {
        runner.withPropertyValues("smartcharging.phases=1").run(context ->
                assertEquals(1, context.getBean(ApplicationConfiguration.class).getSmartChargingPhases()));
    }
}
