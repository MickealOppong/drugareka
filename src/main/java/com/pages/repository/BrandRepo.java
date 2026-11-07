package com.pages.repository;

import com.pages.model.Brand;
import com.pages.model.ListingTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BrandRepo extends JpaRepository<Brand,Long>, JpaSpecificationExecutor<Brand> {
    Optional<Brand> findByName(String name);
    boolean existsByName(String name);
}
