package com.pages.specs;

import com.pages.model.Category;
import com.pages.model.ListingOrder;
import com.pages.model.ListingOrderItem;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

import java.util.Locale;

public class CategorySpecs {

    public static Specification<Category> hasCategoryName(String queryItem) {

        return (root, query, cb) -> {

            if (queryItem == null || queryItem.isBlank()) {
                return cb.conjunction();
            }

            String normalizedSearch = queryItem.trim().toLowerCase(Locale.ROOT);
            String pattern = "%" + normalizedSearch + "%";

            Expression<String> statusAsString =
                    root.get("status").as(String.class);

            return cb.or(
                    cb.like(cb.lower(root.get("name")), pattern),
                    cb.like(cb.lower(root.get("parent")), pattern),
                    cb.like(cb.lower(statusAsString), pattern)
            );
        };
    }
}
