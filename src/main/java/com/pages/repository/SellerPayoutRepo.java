package com.pages.repository;

import com.pages.model.AppUser;
import com.pages.model.SellerPayout;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SellerPayoutRepo extends JpaRepository<SellerPayout,Long> {

    List<SellerPayout> findByListingOrderItemId(Long itemId);
    Page<SellerPayout> findBySellerUserId(Long id, Pageable pageable);
}
