package com.pages.repository;

import com.pages.model.Brand;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BrandRepo extends JpaRepository<Brand,Long> {
    Optional<Brand> findByName(String name);
    boolean existsByName(String name);
}
