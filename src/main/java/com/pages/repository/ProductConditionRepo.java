package com.pages.repository;

import com.pages.model.ProductCondition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductConditionRepo extends JpaRepository<ProductCondition,Long> {

    Optional<ProductCondition> findByName(String name);
    boolean existsByName(String name);
}
