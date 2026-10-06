package com.pages.repository;

import com.pages.model.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepo extends JpaRepository<Category,Long> {

    Optional<Category> findByNameAndParentIsNull(String name);
    Optional<Category> findByName(String name);
    Optional<Category> findBySlug(String slug);
    List<Category> findTop6ByParentIsNullAndIsActiveIsTrueOrderBySortOrder();
    List<Category> findTop8ByParentIsNullAndIsActiveIsTrueOrderBySortOrderAsc();
    List<Category> findAllByAndIsActiveIsTrue();
    boolean existsByNameIgnoreCaseAndParent(String name,Category parent);
    Optional<Category> findByNameIgnoreCaseAndParent(String name,Category parent);

    boolean existsByNameIgnoreCaseAndParentIsNull(String name);
    Optional<Category> findByPath(String path);
}
