package com.pages.specs;


import com.pages.enums.InventoryStatus;
import com.pages.enums.ListingStatus;
import com.pages.model.*;
import jakarta.persistence.criteria.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.Locale;

@Slf4j
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

            Path<Category> parentCategoryPath = categoryJoin.get("parent");

            String lowerCaseCategory = category.toLowerCase().trim();

            // Condition A: Direct match where the category's own slug matches the query parameter
            Predicate directCategoryMatch = cb.equal(
                    cb.lower(categoryJoin.get("slug")),
                    lowerCaseCategory
            );

            // Condition B: Cascading match where the category's parent slug matches the query parameter
            // (This catches all subcategories belonging to that parent group!)
            Predicate parentCategoryMatch = cb.equal(
                    cb.lower(parentCategoryPath.get("slug")),
                    lowerCaseCategory
            );

            return cb.or(directCategoryMatch,parentCategoryMatch);
        };
    }


    public static Specification<ListingTransaction> hasSellerId(Long sellerId) {

        return (root, query, cb) -> {

            if (sellerId == null) {
                return cb.conjunction();
            }

            Join<ListingTransaction, InventoryItem> inventoryJoin =
                    root.join("inventory", JoinType.INNER);
            return cb.equal(
                    inventoryJoin.get("seller").get("id"),
                    sellerId
            );
        };
    }


    /**
     * Filters listings hierarchically.
     * - If categoryId is a parent, fetches all products belonging to its children or itself.
     * - If categoryId is a child, isolates products matching only that specific child.
     */
    public static Specification<ListingTransaction> hasCategoryId(Long categoryId) {
        return (root, query, cb) -> {
            // Guard Check: If categoryId is 0 or null, return an empty conjunction (matches all)
            if (categoryId == null || categoryId == 0) {
                return cb.conjunction();
            }

            // 1. Establish the core inner chain down to the product's assigned category
            Join<ListingTransaction, InventoryItem> inventory = root.join("inventory", JoinType.INNER);
            Join<InventoryItem, ProductCatalog> product = inventory.join("productCatalog", JoinType.INNER);
            Join<ProductCatalog, Category> itemCategory = product.join("category", JoinType.INNER);

            // 2. LEFT JOIN the parent mapping of the item's category to trace roots safely without NPEs
            Join<Category, Category> itemCategoryParent = itemCategory.join("parent", JoinType.LEFT);

            // Condition A: Direct Match (Matches if the item is explicitly in this category)
            Predicate directMatch = cb.equal(itemCategory.get("id"), categoryId);

            // Condition B: Child-of-Parent Match (Matches if the item's parent category matches the query ID)
            Predicate childOfParentMatch = cb.and(
                    cb.isNotNull(itemCategory.get("parent")), // Presence Shield Guard
                    cb.equal(itemCategoryParent.get("id"), categoryId)
            );

            // Combines conditions with a logical OR block.
            // If the query ID is a Parent, Condition B pulls all children.
            // If the query ID is a Child, Condition B safely evaluates to false, and Condition A isolates the specific child records.
            return cb.or(directMatch, childOfParentMatch);
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

    public static Specification<ListingTransaction> isPersonalCollectionAvailable(
            Boolean isCollectionAvailable) {

        return (root, query, cb) -> {

            if (!Boolean.TRUE.equals(isCollectionAvailable)) {
                return cb.conjunction();
            }

            Join<ListingTransaction, InventoryItem> inventoryJoin =
                    root.join("inventory");

            return cb.isTrue(
                    inventoryJoin.get("personalCollectionAvailable")
            );
        };
    }

    public static Specification<ListingTransaction> hasCollectionCity(String location) {

        return (root, query, cb) -> {
            String formatedLocation = location.replace("_",",");

            String city = location.split("_")[0];
            String region = location.split("_")[0];

            if(city.isBlank()){
                return cb.conjunction();
            }

            if (formatedLocation.isBlank()) {
                return cb.conjunction();
            }

            Join<ListingTransaction, InventoryItem> inventoryJoin =
                    root.join("inventory");

            return cb.or(
                    cb.like(
                            cb.lower(inventoryJoin.get("collectionCity")),
                            region.trim().toLowerCase(Locale.ROOT)
                    ),
                    cb.like(
                            cb.lower(inventoryJoin.get("collectionCity")),
                            city.trim().toLowerCase(Locale.ROOT)
                    ),
                    cb.like(
                            cb.lower(inventoryJoin.get("collectionCity")),
                            formatedLocation.trim().toLowerCase(Locale.ROOT)
                    )
            );
        };
    }



    public static Specification<ListingTransaction> hasBrand(String brand) {

        return (root, query, cb) -> {

            if (brand == null || brand.isBlank()) {
                return cb.conjunction();
            }

            Join<ListingTransaction, InventoryItem> inventoryJoin =
                    root.join("inventory");
            Join<InventoryItem, ProductCatalog> product =
                    inventoryJoin.join("productCatalog");

            return cb.equal(
                    cb.lower(product.get("brand").get("name")),
                    brand.trim().toLowerCase(Locale.ROOT)
            );
        };
    }

    public static Specification<ListingTransaction> hasCondition(String condition) {

        return (root, query, cb) -> {

            if (condition == null || condition.isBlank()) {
                return cb.conjunction();
            }

            Join<ListingTransaction, InventoryItem> inventoryJoin =
                    root.join("inventory");

            Join<InventoryItem, ProductCondition> conditionJoin =
                    inventoryJoin.join("productCondition");

            return cb.equal(
                    cb.lower(conditionJoin.get("name")),
                    condition.trim().toLowerCase(Locale.ROOT)
            );
        };
    }

    public static Specification<ListingTransaction> hasName(String name) {

        return (root, query, cb) -> {

            if (name == null || name.isBlank()) {
                return cb.conjunction();
            }

            String normalizedSearch = name.trim().toLowerCase(Locale.ROOT);
            String pattern = "%" + normalizedSearch + "%";

            Join<ListingTransaction, InventoryItem> inventoryJoin =
                    root.join("inventory");


            Join<InventoryItem,ProductCatalog> catalogJoin =
                    inventoryJoin.join("productCatalog");

            return cb.like(
                    cb.lower(catalogJoin.get("name")),
                    pattern.trim().toLowerCase(Locale.ROOT)
            );
        };
    }

    public static Specification<ListingTransaction> matchesSearch(String search) {
        return (root, query, cb) -> {
            if (search == null || search.isBlank()) {
                return cb.conjunction();
            }

            String normalizedSearch = search.trim().toLowerCase(Locale.ROOT);
            String pattern = "%" + normalizedSearch + "%";

            Join<ListingTransaction, InventoryItem> inventory =
                    root.join("inventory", JoinType.INNER);

            Join<InventoryItem, ProductCatalog> product =
                    inventory.join("productCatalog", JoinType.LEFT);

            Join<ProductCatalog, Category> category =
                    product.join("category");

            Join<ProductCatalog, Brand> brand =
                    product.join("brand", JoinType.LEFT);


           return cb.or(
                    cb.like(cb.lower(product.get("name")), pattern),
                    cb.like(cb.lower(brand.get("name")), pattern),
                    cb.like(cb.lower(product.get("description")), pattern),
                    cb.like(cb.lower(inventory.get("model")), pattern),
                    cb.like(cb.lower(inventory.get("itemNumber")), pattern),
                   cb.like(cb.lower(category.get("name")), pattern)
            );
        };
    }


    public static Specification<ListingTransaction> deliveryInfo(String info) {

        return (root, query, cb) -> {

            if (info == null || info.isBlank()) {
                return cb.conjunction();
            }
            String searchPattern = "%" + info.trim().toLowerCase() + "%";

            Join<ListingTransaction, InventoryItem> inventoryJoin =
                    root.join("inventory");

            return cb.like(
                    cb.lower(inventoryJoin.get("deliveryInfo")), info.trim());
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