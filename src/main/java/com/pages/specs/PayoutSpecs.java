package com.pages.specs;

import com.pages.model.*;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

import java.util.Locale;

public class PayoutSpecs {

    public static Specification<SellerPayout> hasSellerUserId(Long sellerId) {

        return (root, query, cb) -> {

            if (sellerId == null) {
                return cb.conjunction();
            }
            Join<SellerPayout, SellerProfile> seller =
                    root.join("seller", JoinType.INNER);
            return cb.equal(
                    seller.get("user").get("id"),
                    sellerId
            );
        };
    }

    public static Specification<SellerPayout> matchQuery(String queryItem) {

        return (root, query, cb) -> {

            if (queryItem == null || queryItem.isBlank()) {
                return cb.conjunction();
            }

            String normalizedSearch = queryItem.trim().toLowerCase(Locale.ROOT);
            String pattern = "%" + normalizedSearch + "%";

            Join<SellerPayout, ListingOrderItem> orderItem =
                    root.join("listingOrderItem", JoinType.INNER);

            Expression<String> statusAsString =
                    root.get("status").as(String.class);

            return cb.or(
                    cb.like(
                            cb.lower(orderItem.get("listingOrder").get("orderNumber")),
                            pattern
                    ),
                    cb.like(cb.lower(statusAsString), pattern)
            );
        };
    }
}
