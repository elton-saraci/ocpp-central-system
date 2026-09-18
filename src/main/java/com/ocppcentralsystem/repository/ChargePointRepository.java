package com.ocppcentralsystem.repository;

import com.ocppcentralsystem.model.ChargePoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface ChargePointRepository extends JpaRepository<ChargePoint, String>, JpaSpecificationExecutor<ChargePoint> {

    /** Looks a station up within one tenant: the same cpId cannot exist in two tenants. */
    Optional<ChargePoint> findByTenantAndCpId(String tenant, String cpId);

    boolean existsByTenantAndCpId(String tenant, String cpId);

    /**
     * Session lookup. Keyed by the session handle alone on purpose: a websocket id is unique and
     * the station it resolves to carries the tenant, which is what the session is scoped by.
     */
    Optional<ChargePoint> findByWebsocketId(UUID websocketId);
}