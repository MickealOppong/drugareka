package com.pages.specs;

import com.pages.model.*;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

import java.util.Locale;

public class SellerProfileSpec {

    public static Specification<SellerProfile> matchesSearch(String search) {
        return (root, query, cb) -> {
            if (search == null || search.isBlank()) {
                return cb.conjunction();
            }

            String normalizedSearch = search.trim().toLowerCase(Locale.ROOT);
            String pattern = "%" + normalizedSearch + "%";



            return cb.or(
                    cb.like(cb.lower(root.get("fullLegalName")), pattern),
                    cb.like(cb.lower(root.get("status")), pattern)
            );
        };
    }
}
