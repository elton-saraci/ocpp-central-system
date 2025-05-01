package com.ocppcentralsystem.model;

import com.ocppcentralsystem.util.ConnectorStatusMapConverter;
import eu.chargetime.ocpp.model.core.ChargePointStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@Entity
@AllArgsConstructor
@NoArgsConstructor
public class ChargePoint {

    @Id
    @Column(nullable = false)
    private String cpId;

    @Column(columnDefinition = "VARCHAR(36)", nullable = false)
    private UUID websocketId;

    @Convert(converter = ConnectorStatusMapConverter.class)
    @Column(columnDefinition = "TEXT")
    private Map<Integer, ChargePointStatus> connectors;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WebsocketConnectionStatus websocketConnectionStatus;

    @Column(nullable = false)
    private LocalDateTime lastUpdated;

}
