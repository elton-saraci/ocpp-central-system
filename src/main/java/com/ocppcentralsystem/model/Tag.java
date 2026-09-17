package com.ocppcentralsystem.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * An authorization tag (RFID card, app tag, ...) belonging to a customer.
 *
 * <p>The {@code idTag} is the identifier the charge point sends in the OCPP
 * {@code Authorize} and {@code StartTransaction} messages, so it doubles as the
 * primary key. OCPP 1.6 limits the identifier to 20 characters.</p>
 */
@Data
@Entity
@Table(name = "tag")
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Tag {

    @Id
    @Column(nullable = false, length = 20)
    private String idTag;

    @Column(nullable = false, length = 100)
    private String customerName;

    @Column(length = 150)
    private String email;

    @Column(length = 30)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TagType tagType;

    /**
     * State controlled by the operator. A tag is only usable when this is
     * {@link TagStatus#ACTIVE} and it has not expired.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TagStatus status;

    /** When the tag stops being valid. {@code null} means it never expires. */
    private LocalDateTime expiryDate;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime lastUpdated;

    /** Stamps an insert. Values set by the caller are kept. */
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

    /** Refreshes {@code lastUpdated} on every update, so callers cannot forget it. */
    @PreUpdate
    void onUpdate() {
        lastUpdated = LocalDateTime.now();
    }

    /** @return true when the tag has an expiry date that is already in the past. */
    public boolean isExpired() {
        return expiryDate != null && expiryDate.isBefore(LocalDateTime.now());
    }

    /** @return true when the tag may be used to start a charging session. */
    public boolean isUsable() {
        return TagStatus.ACTIVE.equals(status) && !isExpired();
    }

}
