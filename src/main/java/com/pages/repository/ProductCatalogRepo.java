package com.pages.repository;

import com.pages.model.ProductCatalog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductCatalogRepo extends JpaRepository<ProductCatalog,Long> {


        boolean existsByNameIgnoreCaseAndBrandNameIgnoreCase(String name1, String name2);
        Optional<ProductCatalog> findByNameIgnoreCaseAndBrandNameIgnoreCase(String name1, String name2);
}
