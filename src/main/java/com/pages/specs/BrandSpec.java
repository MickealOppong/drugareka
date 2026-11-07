package com.pages.specs;

import com.pages.model.*;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.Locale;

public class BrandSpec {

    public static Specification<Brand> hasCategory(String category) {
        return (root, query, cb) -> {

            if (category == null || category.isBlank()||category.equalsIgnoreCase("all")) {
                return cb.conjunction();
            }

            String categorySlug = category.trim().toLowerCase(Locale.ROOT);

            Subquery<Long> subquery = query.subquery(Long.class);

            Root<ListingTransaction> transaction =
                    subquery.from(ListingTransaction.class);

            Join<ListingTransaction, InventoryItem> inventory =
                    transaction.join("inventory");

            Join<InventoryItem, ProductCatalog> catalog =
                    inventory.join("productCatalog");

            Join<ProductCatalog, Category> productCategory =
                    catalog.join("category");

            Join<Category, Category> parentCategory =
                    productCategory.join("parent", JoinType.LEFT);

            subquery.select(transaction.get("id"))
                    .where(
                            cb.and(
                                    cb.or(
                                            cb.equal(
                                                    cb.lower(productCategory.get("slug")),
                                                    categorySlug
                                            ),
                                            cb.equal(
                                                    cb.lower(parentCategory.get("slug")),
                                                    categorySlug
                                            )
                                    ),
                                    cb.equal(
                                            catalog.get("brand").get("id"),
                                            root.get("id")
                                    )
                            )
                    );

            query.distinct(true);

            return cb.exists(subquery);
        };
    }
}
