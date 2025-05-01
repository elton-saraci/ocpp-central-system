package com.ocppcentralsystem.repository;

import com.ocppcentralsystem.model.ChargeTransaction;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface ChargeTransactionRepository extends JpaRepository<ChargeTransaction, Integer> {

    @Transactional
    @Query("""
    SELECT ct FROM ChargeTransaction ct
    WHERE ct.chargePoint.websocketId = :websocketId
      AND ct.idTag = :idTag
      AND ct.isActive = true
    ORDER BY ct.lastUpdated DESC
    """)
    Optional<ChargeTransaction> findLatestByWebsocketIdAndIdTag(
            @Param("websocketId") UUID websocketId,
            @Param("idTag") String idTag
    );

    @Modifying
    @Transactional
    @Query("""
    UPDATE ChargeTransaction ct
    SET ct.meterStop = :meterStop,
        ct.lastUpdated = :lastUpdated,
        ct.latestMeterValue = :meterStop,
        ct.isActive = :isActive
    WHERE ct.chargeTransactionId = :transactionId
    """)
    int updateStopTransaction(@Param("transactionId") int transactionId,
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
    """)
    int updateMeterValues(@Param("transactionId") int transactionId,
                          @Param("meterValue") Integer meterValue,
                          @Param("powerValue") Integer powerValue,
                          @Param("connectorId") Integer connectorId,
                          @Param("lastUpdated") LocalDateTime lastUpdated);

}
