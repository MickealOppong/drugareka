package com.pages.repository;

import com.pages.model.SellerProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SellerProfileRepo extends JpaRepository<SellerProfile,Long> {

    Optional<SellerProfile> findByUserId(Long userId);
}
