package com.ocppcentralsystem.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;

@Data
@Entity
@NoArgsConstructor
public class ChargeTransaction {

    @Id
    @Column(nullable = false)
    private int chargeTransactionId;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "cp_id", referencedColumnName = "cpId", nullable = false)
    private ChargePoint chargePoint;
    private Integer connectorId;
    private Integer meterStart;
    private Integer meterStop;
    private Integer latestMeterValue;
    private Integer latestPowerValue;
    @Column(nullable = false)
    private String idTag;

    @Column(nullable = false)
    private LocalDateTime lastUpdated;

    @Column(nullable = false)
    private boolean isActive;

    public ChargeTransaction(ChargePoint chargePoint, int connectorId, String idTag) {
        this.chargeTransactionId = ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE);
        this.lastUpdated = LocalDateTime.now();
        this.chargePoint = chargePoint;
        this.connectorId = connectorId;
        this.isActive = true;
        this.idTag = idTag;
    }

}
