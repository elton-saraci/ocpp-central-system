package com.ocppcentralsystem.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Result of {@code DELETE /tag/{idTag}}. A tag with transaction history is blocked
 * instead of removed so the history stays readable.
 */
@Data
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TagDeletionResultDTO {

    private String idTag;
    private TagDeletionAction action;
    /** Number of transactions referencing the tag. */
    private long transactionCount;
    /** The tag after the operation; {@code null} when it was deleted. */
    private TagDTO tag;
}
