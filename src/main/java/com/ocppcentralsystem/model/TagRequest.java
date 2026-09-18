package com.ocppcentralsystem.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Payload to create a tag.
 *
 * <p>When updating an existing tag the {@code idTag} in the path wins; the value in the
 * body is only used on creation.</p>
 *
 * <p>Build one in code; JSON is bound through the no-arg constructor and the setters, so the
 * all-args constructor stays package-private to keep seven-argument calls out of the codebase.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PACKAGE)
public class TagRequest {

    @NotBlank(message = "idTag is required")
    @Size(max = 20, message = "idTag must not exceed 20 characters")
    private String idTag;

    @NotBlank(message = "customerName is required")
    @Size(max = 100, message = "customerName must not exceed 100 characters")
    private String customerName;

    @Email(message = "email must be a valid address")
    @Size(max = 150, message = "email must not exceed 150 characters")
    private String email;

    @Size(max = 30, message = "phone must not exceed 30 characters")
    private String phone;

    @NotNull(message = "tagType is required")
    private TagType tagType;

    /** Optional. {@code null} means the tag never expires. */
    private LocalDateTime expiryDate;

    @Size(max = 500, message = "notes must not exceed 500 characters")
    private String notes;
}
