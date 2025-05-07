package com.ocppcentralsystem.config;

import com.ocppcentralsystem.model.ChargePoint;
import com.ocppcentralsystem.model.WebsocketConnectionStatus;
import com.ocppcentralsystem.repository.ChargePointRepository;
import eu.chargetime.ocpp.JSONServer;
import eu.chargetime.ocpp.ServerEvents;
import eu.chargetime.ocpp.model.SessionInformation;
import eu.chargetime.ocpp.model.remotetrigger.TriggerMessageRequest;
import eu.chargetime.ocpp.model.remotetrigger.TriggerMessageRequestType;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.UUID;

@Configuration
@Slf4j
@AllArgsConstructor
public class ServerEventConfig {

	private final JSONServer jsonServer;
	private final ChargePointRepository chargePointRepository;

	@Bean
	public ServerEvents createServerCoreImpl() {
		return getNewServerEventsImpl();
	}

	private ServerEvents getNewServerEventsImpl() {
		return new ServerEvents() {

			/*
			Once we have a successful authentication, the newSession method gets called.
			We trigger the BootNotification and StatusNotifications upon session creation.
			 */
			@Override
			public void newSession(UUID websocketId, SessionInformation information) {
				try {
					log.info("New Session established, sessionIndex -> {}, sessionIdentifier -> {}", websocketId, information.getIdentifier());
					String cpId = information.getIdentifier().substring(1);
					ChargePoint chargePoint = new ChargePoint(cpId, websocketId, new HashMap<>(), WebsocketConnectionStatus.OPEN, LocalDateTime.now());
					chargePointRepository.save(chargePoint);
					TriggerMessageRequest bootNotification = new TriggerMessageRequest(TriggerMessageRequestType.BootNotification);
					TriggerMessageRequest statusNotificationRequest = new TriggerMessageRequest(TriggerMessageRequestType.StatusNotification);
					jsonServer.send(websocketId, bootNotification);
					jsonServer.send(websocketId, statusNotificationRequest);
				} catch (Exception e) {
					log.error("Error occurred while handling new session, error message: " + e.getLocalizedMessage());
				}
			}

			/*
			Once the charge point goes offline, the lostSession method gets called.
			 */
			@Override
			@Transactional
			public void lostSession(UUID sessionIndex) {
				log.info("LostSession event for sessionIndex -> {}", sessionIndex);
				chargePointRepository.updateConnectionStatusByWebsocketId(sessionIndex, WebsocketConnectionStatus.CLOSED);
			}

		};
	}

}
