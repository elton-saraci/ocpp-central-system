package com.ocppcentralsystem.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A charge station registered in the central system.
 *
 * <p>A row exists from the moment an operator registers the station, so it is there before the
 * station ever dials in. The session fields ({@code websocketId}, {@code connectionStatus},
 * {@code lastConnectedAt}) stay empty while it is offline, and only an enabled station is allowed
 * to connect.</p>
 *
 * <p>Columns are kept to the things the code reasons about - identity, the registry gate, the
 * heartbeat interval, session state and what the station reports about itself on boot. Everything
 * else descriptive an operator wants to record goes in {@link #metadata}, so new fields need no
 * schema change.</p>
 */
@Data
@Entity
@Table(name = "charge_point")
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ChargePoint {

    /**
     * The identity the station presents in its connection URL, e.g. {@code CP-1} for
     * {@code wss://host/OCPP16/CP-1}. It may contain slashes, which is why it is never used as a
     * path segment in the API.
     */
    @Id
    @Column(nullable = false, length = 100)
    private String cpId;

    /**
     * The tenant the station belongs to. Only that tenant can see or operate it, and a session
     * inherits the tenant of the station it connected as.
     */
    @Column(nullable = false, length = 50)
    private String tenant;

    /** Label an operator can set; OCPP has no notion of a station name. */
    private String name;

    private String vendor;
    private String model;
    private String serialNumber;
    private String firmwareVersion;

    /** Timezone the station operates in, e.g. {@code Europe/Rome}. */
    private String timezone;

    /**
     * Everything else an operator wants to record: address, coordinates, meter serial, ICCID,
     * asset tags, contact details, ... Free-form on purpose, so new descriptive fields need no
     * schema change.
     *
     * <p>Nothing in the code reads these values - anything the logic depends on belongs in a typed
     * column instead. They are stored as JSON, and JSON is not portable to query into (H2 and
     * Postgres have different operators), so keep the content descriptive rather than
     * load-bearing.</p>
     */
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> metadata;

    /** Whether the station is allowed to connect and charge. */
    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    /** Heartbeat interval handed to the station on BootNotification, in seconds. */
    private Integer heartbeatIntervalSeconds;

    @Column(columnDefinition = "VARCHAR(36)")
    private UUID websocketId;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private WebsocketConnectionStatus connectionStatus;

    private LocalDateTime lastConnectedAt;

    /** Last time anything was heard from the station. */
    @Column(nullable = false)
    private LocalDateTime lastUpdated;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "chargePoint", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 50)
    @Builder.Default
    private List<Connector> connectors = new ArrayList<>();

    /** @return whether the station currently holds a session with this central system. */
    public boolean isConnected() {
        return websocketId != null && WebsocketConnectionStatus.OPEN == connectionStatus;
    }

    /** Adds a connector, keeping both sides of the relation in step. */
    public void addConnector(Connector connector) {
        connector.setChargePoint(this);
        connectors.add(connector);
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (lastUpdated == null) {
            lastUpdated = now;
        }
    }

    @PreUpdate
    void onUpdate() {
        lastUpdated = LocalDateTime.now();
    }
}
