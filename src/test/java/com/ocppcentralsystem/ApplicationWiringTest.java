package com.ocppcentralsystem;

import com.ocppcentralsystem.config.JsonServerImpl;
import eu.chargetime.ocpp.JSONServer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Loads the context the way the application boots in production: with the real OCPP transport.
 *
 * <p>Every other test replaces {@link JSONServer} with a mock, which cuts the transport and its
 * own dependencies out of the bean graph. That kept the suite green while the application refused
 * to start, because the cycle runs through the transport: JSONServer to core profile to
 * {@code OcppCoreHandler} to the services and back to the communicator.</p>
 *
 * <p>Here the transport is the real one; only {@link JsonServerImpl} is mocked, so the wiring is
 * exercised without binding the websocket port.</p>
 */
@SpringBootTest
class ApplicationWiringTest {

    /** Mocked so that nothing opens a socket in a test run. */
    @MockitoBean
    private JsonServerImpl jsonServerImpl;

    @Autowired
    private JSONServer jsonServer;

    @Test
    void theRealTransportIsWiredWithoutACycle() {
        assertNotNull(jsonServer);
    }
}
