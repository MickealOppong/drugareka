package com.pages.repository;

import com.pages.model.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepo extends JpaRepository<Category,Long> {

    Optional<Category> findByName(String name);
    Optional<Category> findBySlug(String slug);
    List<Category> findTop6AllByAndIsActiveIsTrue();
    List<Category> findAllByAndIsActiveIsTrue();
    boolean existsByName(String name);
}
