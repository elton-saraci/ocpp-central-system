package com.ocppcentralsystem.service;

import com.ocppcentralsystem.mapper.ChargeTransactionMapper;
import com.ocppcentralsystem.model.ChargePoint;
import com.ocppcentralsystem.model.ChargeTransaction;
import com.ocppcentralsystem.model.ChargeTransactionDTO;
import com.ocppcentralsystem.model.Tag;
import com.ocppcentralsystem.model.TagAuthorization;
import com.ocppcentralsystem.model.TagDTO;
import com.ocppcentralsystem.model.TagDeletionAction;
import com.ocppcentralsystem.model.TagDeletionResultDTO;
import com.ocppcentralsystem.model.TagRequest;
import com.ocppcentralsystem.model.TagStatus;
import com.ocppcentralsystem.model.TagType;
import com.ocppcentralsystem.repository.ChargePointRepository;
import com.ocppcentralsystem.repository.ChargeTransactionRepository;
import com.ocppcentralsystem.repository.TagRepository;
import com.ocppcentralsystem.support.ChargePointFixtures;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that tags are stored, that transactions reference them through a real
 * foreign key, and that removing a tag never orphans transaction history.
 */
@SpringBootTest
@Transactional
class TagServiceIntegrationTest {

    @Autowired
    private TagService tagService;
    @Autowired
    private TagRepository tagRepository;
    @Autowired
    private ChargePointRepository chargePointRepository;
    @Autowired
    private ChargeTransactionRepository chargeTransactionRepository;
    @Autowired
    private ChargeTransactionMapper chargeTransactionMapper;
    @Autowired
    private EntityManager entityManager;

    @Test
    void transactionReferencesTagAndTagIsBlockedInsteadOfDeleted() {
        tagService.createTag(request("RFID-TEST", "Test Customer", TagType.RFID, LocalDateTime.now().plusDays(1)));
        assertEquals(TagAuthorization.ACCEPTED, tagService.authorize("RFID-TEST"));

        ChargePoint chargePoint = chargePointRepository.save(ChargePointFixtures.connectedStation("CP-TAG-TEST"));

        Tag tag = tagRepository.findById("RFID-TEST").orElseThrow();
        chargeTransactionRepository.save(new ChargeTransaction(chargePoint, 1, tag));
        entityManager.flush();
        entityManager.clear();

        List<ChargeTransaction> transactions = chargeTransactionRepository.findByTag_IdTagOrderByLastUpdatedDesc("RFID-TEST");
        assertEquals(1, transactions.size());
        assertEquals("RFID-TEST", transactions.getFirst().getIdTag());
        assertEquals("Test Customer", transactions.getFirst().getTag().getCustomerName());

        ChargeTransactionDTO dto = chargeTransactionMapper.toDto(transactions.getFirst());
        assertEquals("RFID-TEST", dto.getIdTag());
        assertEquals("CP-TAG-TEST", dto.getCpId());

        assertEquals(1, tagService.findTransactionsByTag("RFID-TEST").size());

        TagDeletionResultDTO result = tagService.deleteTag("RFID-TEST");
        assertEquals(TagDeletionAction.BLOCKED, result.getAction());
        assertEquals(1L, result.getTransactionCount());
        assertTrue(tagRepository.existsById("RFID-TEST"));
        assertEquals(TagStatus.BLOCKED, tagRepository.findById("RFID-TEST").orElseThrow().getStatus());
        assertEquals(TagAuthorization.BLOCKED, tagService.authorize("RFID-TEST"));
    }

    @Test
    void unknownBlockedAndExpiredTagsAreRejected() {
        assertEquals(TagAuthorization.UNKNOWN, tagService.authorize("DOES-NOT-EXIST"));
        assertFalse(tagService.authorize("DOES-NOT-EXIST").isAccepted());

        tagService.createTag(request("BLOCKED-TEST", "Blocked Customer", TagType.RFID, LocalDateTime.now().plusDays(1)));
        tagService.updateTagStatus("BLOCKED-TEST", TagStatus.BLOCKED);
        assertEquals(TagAuthorization.BLOCKED, tagService.authorize("BLOCKED-TEST"));

        tagService.createTag(request("EXPIRED-TEST", "Expired Customer", TagType.APP, LocalDateTime.now().minusDays(1)));
        assertEquals(TagAuthorization.EXPIRED, tagService.authorize("EXPIRED-TEST"));
        assertFalse(tagService.findTagByIdTag("EXPIRED-TEST").isUsable());
    }

    @Test
    void tagWithoutTransactionsIsDeletedForReal() {
        tagService.createTag(request("ORPHAN-TEST", "Orphan Customer", TagType.APP, null));

        TagDeletionResultDTO result = tagService.deleteTag("ORPHAN-TEST");

        assertEquals(TagDeletionAction.DELETED, result.getAction());
        assertEquals(0L, result.getTransactionCount());
        assertFalse(tagRepository.existsById("ORPHAN-TEST"));
    }

    @Test
    void tagWithoutExpiryHasNoExpiryDateInDtoAndStaysUsable() {
        tagService.createTag(request("REMOTE-TEST", "No Expiry Customer", TagType.REMOTE, null));

        var dto = tagService.findTagByIdTag("REMOTE-TEST");

        assertEquals(TagStatus.ACTIVE, dto.getStatus());
        assertEquals("ACTIVE", dto.getEffectiveStatus());
        assertFalse(dto.isExpired());
        assertTrue(dto.isUsable());
    }

    @Test
    void entityMaintainsItsOwnTimestamps() {
        TagDTO created = tagService.createTag(request("STAMP-TEST", "Stamp Customer", TagType.RFID, null));

        // The timestamps survive into the API response, not just the database row.
        assertNotNull(created.getCreatedAt());
        assertNotNull(created.getLastUpdated());

        // Force a stale value, then touch the entity: @PreUpdate must refresh it on flush.
        Tag managed = tagRepository.findById("STAMP-TEST").orElseThrow();
        LocalDateTime stale = LocalDateTime.of(2000, 1, 1, 0, 0);
        managed.setCustomerName("Renamed Customer");
        managed.setLastUpdated(stale);
        entityManager.flush();
        entityManager.clear();

        Tag reloaded = tagRepository.findById("STAMP-TEST").orElseThrow();
        assertEquals("Renamed Customer", reloaded.getCustomerName());
        assertTrue(reloaded.getLastUpdated().isAfter(stale));
    }

    @Test
    void tagListAppliesOnlyTheFiltersThatWereGiven() {
        tagService.createTag(request("FILTER-ACTIVE", "Alice Anderson", TagType.RFID, null));
        tagService.createTag(request("FILTER-BLOCKED", "Bob Brown", TagType.RFID, null));
        tagService.createTag(request("FILTER-APP", "Carol Clark", TagType.APP, null));
        tagService.updateTagStatus("FILTER-BLOCKED", TagStatus.BLOCKED);

        assertEquals(3, tagService.findAllTags(null, null, null).size());
        assertEquals(2, tagService.findAllTags(TagStatus.ACTIVE, null, null).size());
        assertEquals(1, tagService.findAllTags(null, TagType.APP, null).size());
        assertEquals(1, tagService.findAllTags(TagStatus.BLOCKED, TagType.RFID, null).size());

        // search matches the customer name or the tag identifier, ignoring case
        assertEquals(1, tagService.findAllTags(null, null, "anderson").size());
        assertEquals(1, tagService.findAllTags(null, null, "FILTER-APP").size());
        assertEquals(3, tagService.findAllTags(null, null, "filter-").size());
        assertEquals(1, tagService.findAllTags(TagStatus.BLOCKED, null, "bob").size());

        assertTrue(tagService.findAllTags(null, null, "nobody").isEmpty());
        assertTrue(tagService.findAllTags(TagStatus.BLOCKED, TagType.APP, null).isEmpty());
        assertEquals("Alice Anderson", tagService.findAllTags(null, null, null).getFirst().getCustomerName());
    }

    private TagRequest request(String idTag, String customerName, TagType tagType, LocalDateTime expiryDate) {
        return TagRequest.builder()
                .idTag(idTag)
                .customerName(customerName)
                .tagType(tagType)
                .expiryDate(expiryDate)
                .build();
    }
}
