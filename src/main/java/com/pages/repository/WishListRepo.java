package com.pages.repository;

import com.pages.enums.ListingStatus;
import com.pages.model.ListingTransaction;
import com.pages.model.WishList;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WishListRepo extends JpaRepository<WishList,Long> {


    List<WishList> findByUserId(Long userId);
    Optional<WishList> findByUserIdAndListingTransactionId(Long userId, Long listingId);
    Long countByUserId(Long userId);

}
