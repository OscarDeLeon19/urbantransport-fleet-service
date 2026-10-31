package com.urbantransport.fleet.repository;

import com.urbantransport.fleet.domain.RouteEntity;
import jakarta.persistence.criteria.Predicate;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class RouteSpecifications {

    public static Specification<RouteEntity> filter(UUID originBranchId, UUID destinationBranchId) {
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get("deleted")));
            if (originBranchId != null) {
                predicates.add(cb.equal(root.get("originBranchId"), originBranchId));
            }
            if (destinationBranchId != null) {
                predicates.add(cb.equal(root.get("destinationBranchId"), destinationBranchId));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
