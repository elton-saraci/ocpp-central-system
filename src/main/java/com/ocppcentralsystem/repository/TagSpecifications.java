package com.ocppcentralsystem.repository;

import com.ocppcentralsystem.model.Tag;
import com.ocppcentralsystem.model.TagStatus;
import com.ocppcentralsystem.model.TagType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Builds the tag list filter out of only the parts that were supplied.
 *
 * <p>Deliberately not a JPQL query with {@code :param IS NULL OR ...} checks: a null parameter
 * passed to an expression like {@code lower(?)} has no type, and Postgres infers {@code bytea}
 * there and fails with "function lower(bytea) does not exist". Composing predicates avoids
 * binding nulls altogether.</p>
 */
public final class TagSpecifications {

    private TagSpecifications() {
    }

    public static Specification<Tag> matching(TagStatus status, TagType tagType, String search) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (status != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status));
            }
            if (tagType != null) {
                predicates.add(criteriaBuilder.equal(root.get("tagType"), tagType));
            }
            if (search != null) {
                String pattern = "%" + search.toLowerCase(Locale.ROOT) + "%";
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("idTag")), pattern),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("customerName")), pattern)));
            }

            if (predicates.isEmpty()) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
