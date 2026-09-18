package com.ocppcentralsystem.repository;

import com.ocppcentralsystem.model.ChargeTransaction;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChargeTransactionRepository extends JpaRepository<ChargeTransaction, Integer> {

    @Transactional
    @Query("""
    SELECT ct FROM ChargeTransaction ct
    WHERE ct.chargePoint.websocketId = :websocketId
      AND ct.tag.idTag = :idTag
      AND ct.isActive = true
    ORDER BY ct.lastUpdated DESC
    """)
    Optional<ChargeTransaction> findLatestByWebsocketIdAndIdTag(
            @Param("websocketId") UUID websocketId,
            @Param("idTag") String idTag
    );

    /**
     * Resolves the session of the charge point running a transaction without loading the
     * transaction, so callers do not depend on lazy loading being available.
     */
    @Query("""
    SELECT ct.chargePoint.websocketId FROM ChargeTransaction ct
    WHERE ct.chargeTransactionId = :transactionId
    """)
    Optional<UUID> findChargePointWebsocketId(@Param("transactionId") int transactionId);

    List<ChargeTransaction> findByTenantAndTag_IdTagOrderByLastUpdatedDesc(String tenant, String idTag);

    List<ChargeTransaction> findByTenant(String tenant);

    Optional<ChargeTransaction> findByTenantAndChargeTransactionId(String tenant, int chargeTransactionId);

    long countByTenantAndTag_IdTag(String tenant, String idTag);

    long countByTenantAndChargePoint_CpId(String tenant, String cpId);

    @Modifying
    @Transactional
    @Query("""
    UPDATE ChargeTransaction ct
    SET ct.meterStop = :meterStop,
        ct.lastUpdated = :lastUpdated,
        ct.latestMeterValue = :meterStop,
        ct.isActive = :isActive
    WHERE ct.chargeTransactionId = :transactionId
      AND ct.tenant = :tenant
    """)
    int updateStopTransaction(@Param("tenant") String tenant,
                              @Param("transactionId") int transactionId,
                              @Param("meterStop") Integer meterStop,
                              @Param("lastUpdated") LocalDateTime lastUpdated,
                              @Param("isActive") boolean isActive);

    @Modifying
    @Transactional
    @Query("""
    UPDATE ChargeTransaction ct
    SET ct.latestMeterValue = :meterValue,
        ct.latestPowerValue = :powerValue,
        ct.connectorId = :connectorId,
        ct.lastUpdated = :lastUpdated
    WHERE ct.chargeTransactionId = :transactionId
      AND ct.tenant = :tenant
    """)
    int updateMeterValues(@Param("tenant") String tenant,
                          @Param("transactionId") int transactionId,
                          @Param("meterValue") Integer meterValue,
                          @Param("powerValue") Integer powerValue,
                          @Param("connectorId") Integer connectorId,
                          @Param("lastUpdated") LocalDateTime lastUpdated);
}
