package com.pages.repository;

import com.pages.enums.ListingStatus;
import com.pages.model.WishList;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WishListRepo extends JpaRepository<WishList,Long> {

    Optional<WishList> findByListingIdAndUserId(Long inventoryItemId, Long userId);
    List<WishList> findByUserId(Long userId);
    Long countByUserId(Long userId);

}
