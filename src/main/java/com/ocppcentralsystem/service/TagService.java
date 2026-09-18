package com.ocppcentralsystem.service;

import com.ocppcentralsystem.exception.DuplicateResourceException;
import com.ocppcentralsystem.exception.ResourceNotFoundException;
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
import com.ocppcentralsystem.repository.TagSpecifications;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    public List<TagDTO> findAllTags(String tenant, TagStatus status, TagType tagType, String search) {
        String normalizedSearch = (search == null || search.isBlank()) ? null : search.trim();
        return tagMapper.toDtoList(tagRepository.findAll(
                TagSpecifications.matching(tenant, status, tagType, normalizedSearch),
                Sort.by(Sort.Direction.ASC, "customerName")));
    }

    @Transactional(readOnly = true)
    public TagDTO findTagByIdTag(String tenant, String idTag) {
        return tagMapper.toDto(requireTag(tenant, idTag));
    }

    @Transactional(readOnly = true)
    public List<ChargeTransactionDTO> findTransactionsByTag(String tenant, String idTag) {
        requireTag(tenant, idTag);
        return chargeTransactionMapper.toDtoList(
                chargeTransactionRepository.findByTenantAndTag_IdTagOrderByLastUpdatedDesc(tenant, idTag));
    }

    /** @return the tag, or empty when it is not registered. Never throws. */
    @Transactional(readOnly = true)
    public Optional<Tag> findEntityByIdTag(String tenant, String idTag) {
        if (idTag == null || idTag.isBlank()) {
            return Optional.empty();
        }
        return tagRepository.findByTenantAndIdTag(tenant, idTag.trim());
    }

    /**
     * Authorization decision for an incoming OCPP idTag.
     *
     * @param tenant the tenant of the station that asked, so a tag registered by one tenant can
     *               never authorize a station of another.
     */
    public TagAuthorization authorize(String tenant, String idTag) {
        return TagAuthorization.of(findEntityByIdTag(tenant, idTag).orElse(null));
    }

    @Transactional
    public TagDTO createTag(String tenant, TagRequest request) {
        String idTag = request.getIdTag().trim();
        if (tagRepository.existsByTenantAndIdTag(tenant, idTag)) {
            throw new DuplicateResourceException("Tag", idTag);
        }
        if (tagRepository.existsById(idTag)) {
            throw new DuplicateResourceException("Tag", idTag, "the idTag is already used by another tenant");
        }

        Tag tag = Tag.builder()
                .tenant(tenant)
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
        log.info("Created tag {} for customer {} in tenant {}", saved.getIdTag(), saved.getCustomerName(), tenant);
        return tagMapper.toDto(saved);
    }

    @Transactional
    public TagDTO updateTag(String tenant, String idTag, TagRequest request) {
        Tag tag = requireTag(tenant, idTag);

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
    public TagDTO updateTagStatus(String tenant, String idTag, TagStatus status) {
        Tag tag = requireTag(tenant, idTag);
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
    public TagDeletionResultDTO deleteTag(String tenant, String idTag) {
        Tag tag = requireTag(tenant, idTag);
        long transactionCount = chargeTransactionRepository.countByTenantAndTag_IdTag(tenant, idTag);

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

    Tag requireTag(String tenant, String idTag) {
        return tagRepository.findByTenantAndIdTag(tenant, idTag)
                .orElseThrow(() -> new ResourceNotFoundException("Tag", idTag));
    }
}
