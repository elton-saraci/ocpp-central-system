package com.ocppcentralsystem.repository;

import com.ocppcentralsystem.model.ChargePoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface ChargePointRepository extends JpaRepository<ChargePoint, String>, JpaSpecificationExecutor<ChargePoint> {

    Optional<ChargePoint> findByWebsocketId(UUID websocketId);
}