package com.ocppcentralsystem.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TagDTO {

    private String idTag;
    private String customerName;
    private String email;
    private String phone;
    private TagType tagType;
    private TagStatus status;
    /** Effective state: ACTIVE, BLOCKED or EXPIRED. */
    private String effectiveStatus;
    private LocalDateTime expiryDate;
    private boolean expired;
    private boolean usable;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime lastUpdated;
}
