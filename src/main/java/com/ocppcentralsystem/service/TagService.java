package com.ocppcentralsystem.service;

import com.ocppcentralsystem.mapper.ChargeTransactionMapper;
import com.ocppcentralsystem.mapper.TagMapper;
import com.ocppcentralsystem.model.ChargeTransactionDTO;
import com.ocppcentralsystem.model.Tag;
import com.ocppcentralsystem.model.TagAuthorization;
import com.ocppcentralsystem.model.TagDTO;
import com.ocppcentralsystem.model.TagDeletionAction;
import com.ocppcentralsystem.model.TagDeletionResultDTO;
import com.ocppcentralsystem.model.TagRequest;
import com.ocppcentralsystem.model.TagStatus;
import com.ocppcentralsystem.model.TagType;
import com.ocppcentralsystem.repository.ChargeTransactionRepository;
import com.ocppcentralsystem.repository.TagRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

@Service
@AllArgsConstructor
@Slf4j
public class TagService {

    private final TagRepository tagRepository;
    private final ChargeTransactionRepository chargeTransactionRepository;
    private final TagMapper tagMapper;
    private final ChargeTransactionMapper chargeTransactionMapper;

    @Transactional(readOnly = true)
    public List<TagDTO> findAllTags(TagStatus status, TagType tagType, String search) {
        String normalizedSearch = (search == null || search.isBlank()) ? null : search.trim();
        return tagMapper.toDtoList(tagRepository.findAllFiltered(status, tagType, normalizedSearch));
    }

    @Transactional(readOnly = true)
    public TagDTO findTagByIdTag(String idTag) {
        return tagMapper.toDto(requireTag(idTag));
    }

    @Transactional(readOnly = true)
    public List<ChargeTransactionDTO> findTransactionsByTag(String idTag) {
        requireTag(idTag);
        return chargeTransactionMapper.toDtoList(
                chargeTransactionRepository.findByTag_IdTagOrderByLastUpdatedDesc(idTag));
    }

    /** @return the tag, or empty when it is not registered. Never throws. */
    @Transactional(readOnly = true)
    public Optional<Tag> findEntityByIdTag(String idTag) {
        if (idTag == null || idTag.isBlank()) {
            return Optional.empty();
        }
        return tagRepository.findById(idTag.trim());
    }

    /** Authorization decision for an incoming OCPP idTag. */
    public TagAuthorization authorize(String idTag) {
        return TagAuthorization.of(findEntityByIdTag(idTag).orElse(null));
    }

    @Transactional
    public TagDTO createTag(TagRequest request) {
        String idTag = request.getIdTag().trim();
        if (tagRepository.existsById(idTag)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Tag already exists: " + idTag);
        }

        Tag tag = Tag.builder()
                .idTag(idTag)
                .customerName(request.getCustomerName().trim())
                .email(request.getEmail())
                .phone(request.getPhone())
                .tagType(request.getTagType())
                .status(TagStatus.ACTIVE)
                .expiryDate(request.getExpiryDate())
                .notes(request.getNotes())
                .build();

        Tag saved = tagRepository.saveAndFlush(tag);
        log.info("Created tag {} for customer {}", saved.getIdTag(), saved.getCustomerName());
        return tagMapper.toDto(saved);
    }

    @Transactional
    public TagDTO updateTag(String idTag, TagRequest request) {
        Tag tag = requireTag(idTag);

        tag.setCustomerName(request.getCustomerName().trim());
        tag.setEmail(request.getEmail());
        tag.setPhone(request.getPhone());
        tag.setTagType(request.getTagType());
        tag.setExpiryDate(request.getExpiryDate());
        tag.setNotes(request.getNotes());

        Tag saved = tagRepository.saveAndFlush(tag);
        log.info("Updated tag {}", saved.getIdTag());
        return tagMapper.toDto(saved);
    }

    /** Activates or blocks a tag without touching its other fields. */
    @Transactional
    public TagDTO updateTagStatus(String idTag, TagStatus status) {
        Tag tag = requireTag(idTag);
        tag.setStatus(status);

        Tag saved = tagRepository.saveAndFlush(tag);
        log.info("Tag {} status set to {}", saved.getIdTag(), status);
        return tagMapper.toDto(saved);
    }

    /**
     * Removes a tag. When transactions reference it the tag is blocked instead, so
     * that historic transactions keep pointing at a real customer record.
     */
    @Transactional
    public TagDeletionResultDTO deleteTag(String idTag) {
        Tag tag = requireTag(idTag);
        long transactionCount = chargeTransactionRepository.countByTag_IdTag(idTag);

        if (transactionCount > 0) {
            tag.setStatus(TagStatus.BLOCKED);
            Tag blocked = tagRepository.saveAndFlush(tag);
            log.info("Tag {} has {} transaction(s); blocked instead of deleted", idTag, transactionCount);
            return new TagDeletionResultDTO(idTag, TagDeletionAction.BLOCKED, transactionCount, tagMapper.toDto(blocked));
        }

        tagRepository.delete(tag);
        log.info("Deleted tag {}", idTag);
        return new TagDeletionResultDTO(idTag, TagDeletionAction.DELETED, 0, null);
    }

    Tag requireTag(String idTag) {
        return tagRepository.findById(idTag)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tag not found: " + idTag));
    }
}
