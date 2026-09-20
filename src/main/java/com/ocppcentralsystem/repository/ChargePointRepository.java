package com.ocppcentralsystem.repository;

import com.ocppcentralsystem.model.ChargePoint;
import com.ocppcentralsystem.model.Connector;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
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

    /**
     * The connectors registered on a station. Read through the station rather than by connector id
     * so the query stays scoped to the tenant that owns it and cannot leak another tenant's rows.
     */
    @Query("""
            SELECT connector
            FROM ChargePoint station JOIN station.connectors connector
            WHERE station.tenant = :tenant AND station.cpId = :cpId
            """)
    List<Connector> findConnectors(@Param("tenant") String tenant, @Param("cpId") String cpId);
}