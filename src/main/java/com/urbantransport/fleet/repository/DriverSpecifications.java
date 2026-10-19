package com.urbantransport.fleet.repository;

import com.urbantransport.fleet.domain.DriverEntity;
import jakarta.persistence.criteria.Predicate;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class DriverSpecifications {

    public static Specification<DriverEntity> filter(Boolean active, LocalDate expiresFrom, LocalDate expiresTo,
                                                     UUID branchId) {
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (active != null) {
                predicates.add(cb.equal(root.get("active"), active));
            }
            if (expiresFrom != null && expiresTo != null) {
                predicates.add(cb.between(root.get("licenseExpiresOn"), expiresFrom, expiresTo));
            }
            if (branchId != null) {
                predicates.add(cb.equal(root.get("branchId"), branchId));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
