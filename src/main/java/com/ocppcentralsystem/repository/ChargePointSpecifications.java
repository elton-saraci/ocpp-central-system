package com.ocppcentralsystem.repository;

import com.ocppcentralsystem.model.ChargePoint;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * Filters the station registry by the parts that were supplied.
 *
 * <p>Composed rather than written as a JPQL query with {@code :param IS NULL} checks: a null bind
 * parameter has no type on Postgres, which infers {@code bytea} and fails.</p>
 */
public final class ChargePointSpecifications {

    private ChargePointSpecifications() {
    }

    /**
     * @param tenant the tenant whose stations to list, always applied - it is never a filter a
     *               caller can leave out.
     */
    public static Specification<ChargePoint> matching(String tenant, String cpId, Boolean enabled) {
        return (root, _, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.equal(root.get("tenant"), tenant));

            if (cpId != null) {
                predicates.add(criteriaBuilder.equal(root.get("cpId"), cpId));
            }
            if (enabled != null) {
                predicates.add(criteriaBuilder.equal(root.get("enabled"), enabled));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
