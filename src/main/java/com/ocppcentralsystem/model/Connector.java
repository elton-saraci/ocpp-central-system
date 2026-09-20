package com.ocppcentralsystem.model;

import eu.chargetime.ocpp.model.core.ChargePointErrorCode;
import eu.chargetime.ocpp.model.core.ChargePointStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * One connector of a charge point, registered up front by an operator.
 *
 * <p>Holds both the description of the hardware (which limits apply) and the last state the
 * charge point reported, so the state is still visible while the station is offline.</p>
 *
 * <p>OCPP 1.6 addresses connectors by a number, where 0 means the station itself; those are never
 * stored as rows. There is no EVSE level here because OCPP 1.6 has no such concept - if stations
 * ever need to be published to an OCPI hub, its EVSE grouping would sit between the station and
 * these rows.</p>
 */
@Getter
@Setter
@ToString(onlyExplicitlyIncluded = true)
@Entity
@Table(name = "connector", uniqueConstraints = @UniqueConstraint(
        name = "uk_connector_station_number", columnNames = {"cp_id", "connector_id"}))
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Connector {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "cp_id", referencedColumnName = "cpId", nullable = false)
    private ChargePoint chargePoint;

    /** The OCPP 1.6 connector number, 1 or higher. 0 is the station and never gets a row. */
    @Column(name = "connector_id", nullable = false)
    @ToString.Include
    private int connectorId;

    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private ConnectorType type;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ConnectorFormat format;

    /** Single phase, three-phase or DC. Decides how watts convert to amperes. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private PowerType powerType;

    private Integer maxVoltage;
    private Integer maxAmperage;
    /** Maximum power in watts. */
    private Integer maxElectricPower;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    @ToString.Include
    private ChargePointStatus status;

    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private ChargePointErrorCode errorCode;

    private String statusInfo;

    private LocalDateTime lastStatusAt;
}
