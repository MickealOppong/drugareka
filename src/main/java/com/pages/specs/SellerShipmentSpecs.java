package com.pages.specs;

import com.pages.model.*;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

import java.util.Locale;

public class SellerShipmentSpecs {

    public static Specification<SellerShipment> hasSellerId(Long sellerId) {

        return (root, query, cb) -> {

            if (sellerId == null) {
                return cb.conjunction();
            }


            Join<SellerShipment, SellerProfile> sellerJoin =
                    root.join("seller", JoinType.LEFT);

            return cb.equal(
                    sellerJoin.get("id"),
                    sellerId
            );
        };
    }

    public static Specification<SellerShipment> matchQuery(String queryItem) {

        return (root, query, cb) -> {

            if (queryItem == null || queryItem.isBlank()) {
                return cb.conjunction();
            }

            String normalizedSearch = queryItem.trim().toLowerCase(Locale.ROOT);
            String pattern = "%" + normalizedSearch + "%";

            Join<SellerShipment, ListingOrderItem> orderJoin =
                    root.join("listingOrderItem", JoinType.INNER);

            Expression<String> statusAsString =
                    root.get("shipmentStatus").as(String.class);

            return cb.or(
                    cb.like(cb.lower(root.get("shippingMethod")), pattern),
                    cb.like(
                            cb.lower(orderJoin.get("listingOrder").get("orderNumber")),
                            pattern
                    ),
                    cb.like(cb.lower(statusAsString), pattern)
            );
        };
    }
}
