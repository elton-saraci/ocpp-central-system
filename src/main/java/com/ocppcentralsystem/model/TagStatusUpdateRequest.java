package com.ocppcentralsystem.model;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TagStatusUpdateRequest {

    @NotNull(message = "status is required")
    private TagStatus status;
}
