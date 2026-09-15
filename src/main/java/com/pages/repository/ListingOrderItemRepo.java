package com.pages.repository;

import com.pages.model.ListingOrderItem;
import com.pages.model.SellerProfile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ListingOrderItemRepo extends JpaRepository<ListingOrderItem,Long> {

    List<ListingOrderItem> findByListingOrderId(Long orderId);
    Page<ListingOrderItem> findBySellerId(Long sellerId,Pageable pageable);
    Page<ListingOrderItem> findBySeller(SellerProfile seller, Pageable pageable);

}
