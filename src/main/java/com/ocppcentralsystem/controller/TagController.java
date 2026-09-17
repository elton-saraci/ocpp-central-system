package com.ocppcentralsystem.controller;

import com.ocppcentralsystem.model.ChargeTransactionDTO;
import com.ocppcentralsystem.model.TagDTO;
import com.ocppcentralsystem.model.TagDeletionResultDTO;
import com.ocppcentralsystem.model.TagRequest;
import com.ocppcentralsystem.model.TagStatus;
import com.ocppcentralsystem.model.TagStatusUpdateRequest;
import com.ocppcentralsystem.model.TagType;
import com.ocppcentralsystem.service.TagService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/tag")
@AllArgsConstructor
@Validated
public class TagController {

    private final TagService tagService;

    /** Lists tags, optionally filtered by status, type or a free-text search term. */
    @GetMapping
    public ResponseEntity<List<TagDTO>> fetchTags(
            @RequestParam(required = false) TagStatus status,
            @RequestParam(required = false) TagType tagType,
            @RequestParam(required = false) String search) {
        log.info("Fetching tags, status -> {}, tagType -> {}, search -> {}", status, tagType, search);
        return ResponseEntity.ok(tagService.findAllTags(status, tagType, search));
    }

    @GetMapping("/{idTag}")
    public ResponseEntity<TagDTO> fetchTagByIdTag(@PathVariable String idTag) {
        log.info("Fetching tag -> {}", idTag);
        return ResponseEntity.ok(tagService.findTagByIdTag(idTag));
    }

    /** Lists the transactions that used this tag, newest first. */
    @GetMapping("/{idTag}/transactions")
    public ResponseEntity<List<ChargeTransactionDTO>> fetchTransactionsByTag(@PathVariable String idTag) {
        log.info("Fetching transactions for tag -> {}", idTag);
        return ResponseEntity.ok(tagService.findTransactionsByTag(idTag));
    }

    @PostMapping
    public ResponseEntity<TagDTO> createTag(@RequestBody @Valid TagRequest tagRequest) {
        log.info("Creating tag -> {}", tagRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(tagService.createTag(tagRequest));
    }

    /** Updates the customer details of a tag. The idTag itself cannot be changed. */
    @PutMapping("/{idTag}")
    public ResponseEntity<TagDTO> updateTag(@PathVariable String idTag,
                                           @RequestBody @Valid TagRequest tagRequest) {
        log.info("Updating tag -> {} with {}", idTag, tagRequest);
        return ResponseEntity.ok(tagService.updateTag(idTag, tagRequest));
    }

    /** Activates or blocks a tag, e.g. {@code {"status": "BLOCKED"}}. */
    @PatchMapping("/{idTag}/status")
    public ResponseEntity<TagDTO> updateTagStatus(@PathVariable String idTag,
                                                  @RequestBody @Valid TagStatusUpdateRequest request) {
        log.info("Updating status of tag -> {} to {}", idTag, request.getStatus());
        return ResponseEntity.ok(tagService.updateTagStatus(idTag, request.getStatus()));
    }

    /**
     * Deletes a tag. Tags with transaction history are blocked instead of removed;
     * check the returned {@code action} to see which happened.
     */
    @DeleteMapping("/{idTag}")
    public ResponseEntity<TagDeletionResultDTO> deleteTag(@PathVariable String idTag) {
        log.info("Deleting tag -> {}", idTag);
        return ResponseEntity.ok(tagService.deleteTag(idTag));
    }
}
