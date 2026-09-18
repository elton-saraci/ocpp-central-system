package com.ocppcentralsystem.config;

import com.ocppcentralsystem.service.ChargePointRegistryService;
import eu.chargetime.ocpp.JSONServer;
import eu.chargetime.ocpp.ServerEvents;
import eu.chargetime.ocpp.model.SessionInformation;
import eu.chargetime.ocpp.model.remotetrigger.TriggerMessageRequest;
import eu.chargetime.ocpp.model.remotetrigger.TriggerMessageRequestType;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.UUID;

@Configuration
@Slf4j
@AllArgsConstructor
public class ServerEventConfig {

	private final JSONServer jsonServer;
	private final ChargePointRegistryService chargePointRegistryService;

	@Bean
	public ServerEvents createServerCoreImpl() {
		return getNewServerEventsImpl();
	}

	private ServerEvents getNewServerEventsImpl() {
		return new ServerEvents() {

			/*
			Once the WebSocket handshake is done the newSession method gets called. Only a station that
			is registered and enabled is allowed to stay - anything else is disconnected on the spot.
			We trigger the BootNotification and StatusNotifications upon session creation.
			 */
			@Override
			public void newSession(UUID websocketId, SessionInformation information) {
				try {
					String cpId = cpIdFrom(information);
					log.info("New Session established, sessionIndex -> {}, sessionIdentifier -> {}", websocketId, cpId);

					if (!chargePointRegistryService.registerSession(cpId, websocketId)) {
						jsonServer.closeSession(websocketId);
						return;
					}

					TriggerMessageRequest bootNotification = new TriggerMessageRequest(TriggerMessageRequestType.BootNotification);
					TriggerMessageRequest statusNotificationRequest = new TriggerMessageRequest(TriggerMessageRequestType.StatusNotification);
					jsonServer.send(websocketId, bootNotification);
					jsonServer.send(websocketId, statusNotificationRequest);
				} catch (Exception e) {
					log.error("Error occurred while handling new session, error message: {}", e.getLocalizedMessage());
				}
			}

			/*
			Once the charge point goes offline, the lostSession method gets called.
			 */
			@Override
			public void lostSession(UUID sessionIndex) {
				log.info("LostSession event for sessionIndex -> {}", sessionIndex);
				chargePointRegistryService.markSessionClosed(sessionIndex);
			}

		};
	}

	/**
	 * The identity is the path the station connected to, so {@code wss://host/OCPP16/CP-1} identifies
	 * itself as {@code CP-1}. It may contain slashes, and the registry keys on it as it is.
	 */
	private static String cpIdFrom(SessionInformation information) {
		String identifier = information.getIdentifier();
		return identifier != null && identifier.startsWith("/") ? identifier.substring(1) : identifier;
	}
}
