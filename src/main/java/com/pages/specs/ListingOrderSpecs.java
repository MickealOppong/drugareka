package com.pages.specs;

import com.pages.model.AppUser;
import com.pages.model.ListingOrder;
import com.pages.model.ListingOrderItem;
import com.pages.model.SellerProfile;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.domain.Specification;

import java.util.Locale;
@Slf4j
public class ListingOrderSpecs {

    public static Specification<ListingOrderItem> hasSellerId(Long sellerId) {

        return (root, query, cb) -> {

            if (sellerId == null) {
                return cb.conjunction();
            }


            Join<ListingOrderItem, SellerProfile> sellerJoin =
                    root.join("seller", JoinType.LEFT);

            return cb.equal(
                    sellerJoin.get("id"),
                    sellerId
            );
        };
    }

    public static Specification<ListingOrder> hasBuyerId(Long buyerId) {

        return (root, query, cb) -> {

            if (buyerId == null) {
                return cb.conjunction();
            }

            return cb.equal(
                    root.get("buyer").get("id"),
                    buyerId
            );
        };
    }

    public static Specification<ListingOrderItem> hasSearchItemInListingOrderItem(String queryItem) {

        return (root, query, cb) -> {

            if (queryItem == null || queryItem.isBlank()) {
                return cb.conjunction();
            }

            String normalizedSearch = queryItem.trim().toLowerCase(Locale.ROOT);
            String pattern = "%" + normalizedSearch + "%";

            Join<ListingOrderItem, ListingOrder> orderJoin =
                    root.join("listingOrder", JoinType.LEFT);

            Expression<String> statusAsString =
                    orderJoin.get("orderStatus").as(String.class);


            return cb.or(
                    cb.like(cb.lower(orderJoin.get("orderNumber")), pattern),
                    cb.like(cb.lower(statusAsString), pattern)
            );
        };
    }

    public static Specification<ListingOrder> hasSearchItemInListingOrder(String queryItem) {

        return (root, query, cb) -> {

            if (queryItem == null || queryItem.isBlank()) {
                return cb.conjunction();
            }

            String normalizedSearch = queryItem.trim().toLowerCase(Locale.ROOT);
            String pattern = "%" + normalizedSearch + "%";

            Expression<String> statusAsString =
                   root.get("orderStatus").as(String.class);


            return cb.or(
                    cb.like(cb.lower(root.get("orderNumber")), pattern),
                    cb.like(cb.lower(statusAsString), pattern)
            );
        };
    }

    public static Specification<ListingOrderItem> hasOrderStatus(String status) {

        return (root, query, cb) -> {

            if (status == null || status.isBlank()) {
                return cb.conjunction();
            }

            Join<ListingOrderItem, ListingOrder> orderJoin =
                    root.join("listingOrder");

            return cb.like(
                    cb.lower(orderJoin.get("orderStatus")),
                    status.trim().toLowerCase(Locale.ROOT)
            );
        };
    }
}
