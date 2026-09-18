package com.ocppcentralsystem.service;

import com.ocppcentralsystem.exception.DuplicateResourceException;
import com.ocppcentralsystem.exception.InvalidRequestException;
import com.ocppcentralsystem.exception.ResourceNotFoundException;
import com.ocppcentralsystem.mapper.ChargePointMapper;
import com.ocppcentralsystem.model.ChargePoint;
import com.ocppcentralsystem.model.ChargePointDTO;
import com.ocppcentralsystem.model.ChargePointDeletionAction;
import com.ocppcentralsystem.model.ChargePointDeletionResultDTO;
import com.ocppcentralsystem.model.ChargePointRequest;
import com.ocppcentralsystem.model.Connector;
import com.ocppcentralsystem.model.ConnectorRequest;
import com.ocppcentralsystem.model.WebsocketConnectionStatus;
import com.ocppcentralsystem.repository.ChargePointRepository;
import com.ocppcentralsystem.repository.ChargePointSpecifications;
import com.ocppcentralsystem.repository.ChargeTransactionRepository;
import eu.chargetime.ocpp.model.core.BootNotificationRequest;
import eu.chargetime.ocpp.model.core.StatusNotificationRequest;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Owns the station registry: the CRUD an operator drives, and the policy that only a registered,
 * enabled station may hold a session.
 *
 * <p>Connection bookkeeping lives here too, because that is where the decision is made: the OCPP
 * event handler asks {@link #registerSession} whether to accept a socket, and this class records
 * what the station reports once it is on.</p>
 */
@Service
@AllArgsConstructor
@Slf4j
public class ChargePointRegistryService {

    private static final int DEFAULT_HEARTBEAT_INTERVAL_SECONDS = 60;

    private final ChargePointRepository chargePointRepository;
    private final ChargeTransactionRepository chargeTransactionRepository;
    private final ChargePointMapper mapper;

    @Transactional(readOnly = true)
    public List<ChargePointDTO> findAllStations(String tenant, String cpId, Boolean enabled) {
        return mapper.toDtoList(chargePointRepository.findAll(
                ChargePointSpecifications.matching(tenant, cpId, enabled), Sort.by("cpId")));
    }

    /**
     * @throws ResourceNotFoundException when the station is not registered in that tenant.
     */
    @Transactional(readOnly = true)
    public ChargePoint requireStation(String tenant, String cpId) {
        return chargePointRepository.findByTenantAndCpId(tenant, cpId)
                .orElseThrow(() -> new ResourceNotFoundException("Charge point", cpId));
    }

    /**
     * Resolves the station behind a live session, for handlers that only know the session handle.
     *
     * @throws ResourceNotFoundException when no station holds that session.
     */
    @Transactional(readOnly = true)
    public ChargePoint requireStationForSession(UUID websocketId) {
        return chargePointRepository.findByWebsocketId(websocketId)
                .orElseThrow(() -> new ResourceNotFoundException("Charge point", websocketId));
    }
    /**
     * @return the tenant of the station holding this session, or empty when no registered station
     *         does. OCPP messages carry no tenant of their own, so the station row decides which
     *         tenant a session acts for.
     */
    @Transactional(readOnly = true)
    public Optional<String> findSessionTenant(UUID websocketId) {
        return chargePointRepository.findByWebsocketId(websocketId)
                .map(ChargePoint::getTenant);
    }
    @Transactional
    public ChargePointDTO createStation(String tenant, ChargePointRequest request) {
        String cpId = request.getCpId().trim();
        if (chargePointRepository.existsByTenantAndCpId(tenant, cpId)) {
            throw new DuplicateResourceException("Charge point", cpId);
        }
        if (chargePointRepository.existsById(cpId)) {
            throw new DuplicateResourceException("Charge point", cpId,
                    "the cpId is already used by another tenant");
        }

        ChargePoint station = ChargePoint.builder()
                .tenant(tenant)
                .cpId(cpId)
                .enabled(request.getEnabled() == null || request.getEnabled())
                .connectors(new ArrayList<>())
                .build();
        applyDetails(request, station);

        ChargePoint saved = chargePointRepository.saveAndFlush(station);
        log.info("Registered station {} in tenant {} with {} connector(s)",
                saved.getCpId(), tenant, saved.getConnectors().size());
        return mapper.toDto(saved);
    }

    /**
     * Updates everything but the identity: the {@code cpId} is what the station authenticates with,
     * so changing it would orphan the station rather than rename it.
     */
    @Transactional
    public ChargePointDTO updateStation(String tenant, String cpId, ChargePointRequest request) {
        ChargePoint station = requireStation(tenant, cpId);
        applyDetails(request, station);
        if (request.getEnabled() != null) {
            station.setEnabled(request.getEnabled());
        }

        ChargePoint saved = chargePointRepository.saveAndFlush(station);
        log.info("Updated station {}", saved.getCpId());
        return mapper.toDto(saved);
    }

    @Transactional
    public ChargePointDTO setStationEnabled(String tenant, String cpId, boolean enabled) {
        ChargePoint station = requireStation(tenant, cpId);
        station.setEnabled(enabled);

        ChargePoint saved = chargePointRepository.saveAndFlush(station);
        log.info("Station {} {}", cpId, enabled ? "enabled" : "disabled");
        return mapper.toDto(saved);
    }

    /**
     * Removes a station. One with transaction history is disabled instead, so those transactions
     * keep a station to point at.
     */
    @Transactional
    public ChargePointDeletionResultDTO deleteStation(String tenant, String cpId) {
        ChargePoint station = requireStation(tenant, cpId);
        long transactionCount = chargeTransactionRepository.countByTenantAndChargePoint_CpId(tenant, cpId);

        if (transactionCount > 0) {
            station.setEnabled(false);
            ChargePoint disabled = chargePointRepository.saveAndFlush(station);
            log.info("Station {} has {} transaction(s); disabled instead of deleted", cpId, transactionCount);
            return new ChargePointDeletionResultDTO(cpId, ChargePointDeletionAction.DISABLED, transactionCount,
                    mapper.toDto(disabled));
        }

        chargePointRepository.delete(station);
        log.info("Deleted station {}", cpId);
        return new ChargePointDeletionResultDTO(cpId, ChargePointDeletionAction.DELETED, 0, null);
    }

    /**
     * Records a station that has just connected.
     *
     * @return {@code false} when the station is unknown or disabled, in which case the caller has
     *         to refuse the connection.
     */
    @Transactional
    public boolean registerSession(String cpId, UUID websocketId) {
        Optional<ChargePoint> registered = chargePointRepository.findById(cpId);
        if (registered.isEmpty()) {
            log.warn("Refusing connection: no station registered with id {}", cpId);
            return false;
        }

        ChargePoint station = registered.get();
        if (!station.isEnabled()) {
            log.warn("Refusing connection: station {} is disabled", cpId);
            return false;
        }

        station.setWebsocketId(websocketId);
        station.setConnectionStatus(WebsocketConnectionStatus.OPEN);
        station.setLastConnectedAt(LocalDateTime.now());
        chargePointRepository.saveAndFlush(station);

        log.info("Station {} connected, sessionIndex {}", cpId, websocketId);
        return true;
    }

    /** Records that a session ended and forgets its handle. */
    @Transactional
    public void markSessionClosed(UUID websocketId) {
        chargePointRepository.findByWebsocketId(websocketId).ifPresentOrElse(station -> {
            station.setConnectionStatus(WebsocketConnectionStatus.CLOSED);
            station.setWebsocketId(null);
            chargePointRepository.saveAndFlush(station);
            log.info("Station {} disconnected", station.getCpId());
        }, () -> log.warn("No station found for sessionIndex {}", websocketId));
    }

    /**
     * Stores what the station reports about itself on boot.
     *
     * @return the heartbeat interval to answer with, or empty when the station is unknown or was
     *         disabled while connected - in which case the answer is {@code Rejected}.
     */
    @Transactional
    public Optional<Integer> recordBootNotification(UUID websocketId, BootNotificationRequest request) {
        Optional<ChargePoint> session = chargePointRepository.findByWebsocketId(websocketId);
        if (session.isEmpty()) {
            log.warn("BootNotification for unknown sessionIndex {}", websocketId);
            return Optional.empty();
        }

        ChargePoint station = session.get();
        if (!station.isEnabled()) {
            log.warn("BootNotification from disabled station {}", station.getCpId());
            return Optional.empty();
        }

        station.setVendor(request.getChargePointVendor());
        station.setModel(request.getChargePointModel());
        station.setSerialNumber(request.getChargePointSerialNumber());
        station.setFirmwareVersion(request.getFirmwareVersion());
        chargePointRepository.saveAndFlush(station);

        log.info("Station {} booted: {} {} ({}), firmware {}", station.getCpId(), station.getVendor(),
                station.getModel(), station.getSerialNumber(), station.getFirmwareVersion());
        return Optional.of(heartbeatInterval(station));
    }

    @Transactional
    public void recordHeartbeat(UUID websocketId) {
        chargePointRepository.findByWebsocketId(websocketId).ifPresentOrElse(station -> {
            // Set explicitly: a heartbeat changes nothing else, and @PreUpdate only runs for a
            // dirty entity.
            station.setLastUpdated(LocalDateTime.now());
            chargePointRepository.save(station);
        }, () -> log.warn("Heartbeat for unknown sessionIndex {}", websocketId));
    }

    /**
     * Stores the connector state a station reported. Only connectors that were registered receive
     * updates; anything else is a configuration gap worth logging rather than silently accepting.
     */
    @Transactional
    public void recordConnectorStatus(UUID websocketId, StatusNotificationRequest request) {
        Optional<ChargePoint> session = chargePointRepository.findByWebsocketId(websocketId);
        if (session.isEmpty()) {
            log.warn("StatusNotification for unknown sessionIndex {}", websocketId);
            return;
        }

        ChargePoint station = session.get();
        Integer reportedConnectorId = request.getConnectorId();
        Optional<Connector> connector = station.getConnectors().stream()
                .filter(candidate -> Integer.valueOf(candidate.getConnectorId()).equals(reportedConnectorId))
                .findFirst();

        if (connector.isEmpty()) {
            log.warn("Station {} reported status for connector {}, which is not registered on it",
                    station.getCpId(), reportedConnectorId);
            station.setLastUpdated(LocalDateTime.now());
            chargePointRepository.save(station);
            return;
        }

        Connector target = connector.get();
        target.setStatus(request.getStatus());
        target.setErrorCode(request.getErrorCode());
        target.setStatusInfo(request.getInfo());
        target.setLastStatusAt(LocalDateTime.now());
        station.setLastUpdated(LocalDateTime.now());
        chargePointRepository.saveAndFlush(station);

        log.info("Station {} connector {} is now {}", station.getCpId(), target.getConnectorId(), target.getStatus());
    }

    private void applyDetails(ChargePointRequest request, ChargePoint station) {
        station.setName(request.getName());
        station.setVendor(request.getVendor());
        station.setModel(request.getModel());
        station.setSerialNumber(request.getSerialNumber());
        station.setFirmwareVersion(request.getFirmwareVersion());
        station.setTimezone(request.getTimezone());
        station.setMetadata(request.getMetadata() == null ? null : new LinkedHashMap<>(request.getMetadata()));
        station.setHeartbeatIntervalSeconds(request.getHeartbeatIntervalSeconds());
        applyConnectors(station, request.getConnectors());
    }

    /**
     * Updates the connector list in place: connectors that stay configured keep their last known
     * state, new ones are added, and the ones left out are removed.
     */
    private void applyConnectors(ChargePoint station, List<ConnectorRequest> requested) {
        Map<Integer, Connector> existing = station.getConnectors().stream()
                .collect(Collectors.toMap(Connector::getConnectorId, Function.identity()));

        Set<Integer> seen = new HashSet<>();
        for (ConnectorRequest request : requested == null ? List.<ConnectorRequest>of() : requested) {
            if (!seen.add(request.getConnectorId())) {
                throw new InvalidRequestException("Connector " + request.getConnectorId()
                        + " is listed more than once",
                        List.of("connectorId " + request.getConnectorId() + " is duplicated"));
            }

            Connector connector = existing.remove(request.getConnectorId());
            if (connector == null) {
                connector = Connector.builder().connectorId(request.getConnectorId()).build();
                station.addConnector(connector);
            }
            connector.setType(request.getType());
            connector.setFormat(request.getFormat());
            connector.setPowerType(request.getPowerType());
            connector.setMaxVoltage(request.getMaxVoltage());
            connector.setMaxAmperage(request.getMaxAmperage());
            connector.setMaxElectricPower(request.getMaxElectricPower());
        }

        // Anything the caller left out is no longer part of the station.
        station.getConnectors().removeAll(existing.values());
    }

    private int heartbeatInterval(ChargePoint station) {
        return station.getHeartbeatIntervalSeconds() == null
                ? DEFAULT_HEARTBEAT_INTERVAL_SECONDS
                : station.getHeartbeatIntervalSeconds();
    }
}
