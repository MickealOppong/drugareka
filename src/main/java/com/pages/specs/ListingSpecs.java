package com.pages.specs;


import com.pages.enums.InventoryStatus;
import com.pages.enums.ListingStatus;
import com.pages.model.*;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

public class ListingSpecs {

    public static Specification<ListingTransaction> hasListingStatus(
            ListingStatus status) {

        return (root, query, cb) ->
                cb.equal(root.get("listingStatus"), status);
    }

    public static Specification<ListingTransaction> hasCategory(
            String category) {

        return (root, query, cb) -> {

            if (category == null || category.isBlank()) {
                return cb.conjunction();
            }

            Join<ListingTransaction, InventoryItem> inventory =
                    root.join("inventory");

            Join<InventoryItem, ProductCatalog> product =
                    inventory.join("productCatalog");

            Join<ProductCatalog, Category> categoryJoin =
                    product.join("category");

            return cb.equal(
                    cb.lower(categoryJoin.get("slug")),
                    category.toLowerCase()
            );
        };
    }


    public static Specification<ListingTransaction> isNotSold() {

        return (root, query, cb) -> {

            Join<ListingTransaction, InventoryItem> inventoryJoin =
                    root.join("inventory");

            return cb.notEqual(
                    cb.lower(inventoryJoin.get("status")), InventoryStatus.SOLD.name());
        };
    }
    public static Specification<ListingTransaction> excludeSeller(
            Long sellerId) {

        return (root, query, cb) -> {

            if (sellerId == null) {
                return cb.conjunction();
            }

            Join<ListingTransaction, InventoryItem> inventory =
                    root.join("inventory");

            Join<InventoryItem, SellerProfile> seller =
                    inventory.join("seller");

            return cb.notEqual(
                    seller.get("user").get("id"),
                    sellerId
            );
        };
    }
}