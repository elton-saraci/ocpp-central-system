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

    /**
     * The tag that started the session. Fetched eagerly so that DTO mapping and JSON
     * serialization can read the customer name without an open persistence context.
     */
    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "id_tag", referencedColumnName = "idTag", nullable = false)
    private Tag tag;

    @Column(nullable = false)
    private LocalDateTime lastUpdated;

    @Column(nullable = false)
    private boolean isActive;

    public ChargeTransaction(ChargePoint chargePoint, int connectorId, Tag tag) {
        this.chargeTransactionId = ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE);
        this.lastUpdated = LocalDateTime.now();
        this.chargePoint = chargePoint;
        this.connectorId = connectorId;
        this.isActive = true;
        this.tag = tag;
    }

    /** Convenience accessor for the OCPP identifier of the tag. */
    public String getIdTag() {
        return tag != null ? tag.getIdTag() : null;
    }
}
