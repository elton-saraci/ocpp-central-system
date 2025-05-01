package com.ocppcentralsystem.config;

import eu.chargetime.ocpp.JSONServer;
import eu.chargetime.ocpp.ServerEvents;
import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;


@Slf4j
@Component
@AllArgsConstructor
public class JsonServerImpl {

    private final ServerEvents serverEvents;
    private final JSONServer server;
    private final ApplicationConfiguration applicationConfiguration;

    @PostConstruct
    public void startServer() {
        try {
            log.info("Starting JSON server (websocket) on port: {}", applicationConfiguration.getWebsocketPort());
            server.open("localhost", applicationConfiguration.getWebsocketPort(), serverEvents);
        } catch (Exception e) {
            log.error("Exception while starting the server, error message: " + e.getLocalizedMessage());
        }
    }

}
