package com.ocppcentralsystem.repository;


import com.ocppcentralsystem.model.ChargePoint;
import com.ocppcentralsystem.model.WebsocketConnectionStatus;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface ChargePointRepository extends JpaRepository<ChargePoint, String> {

    Optional<ChargePoint> findByWebsocketId(UUID websocketId);

    @Modifying
    @Transactional
    @Query("UPDATE ChargePoint cp SET cp.websocketConnectionStatus = :status WHERE cp.websocketId = :sessionIndex")
    void updateConnectionStatusByWebsocketId(@Param("sessionIndex") UUID sessionIndex,
                                             @Param("status") WebsocketConnectionStatus status);

    @Modifying
    @Transactional
    @Query("UPDATE ChargePoint cp SET cp.lastUpdated = :lastUpdated WHERE cp.websocketId = :websocketId")
    int updateLastUpdatedByWebsocketId(@Param("websocketId") UUID websocketId,
                                      @Param("lastUpdated") LocalDateTime lastUpdated);


}