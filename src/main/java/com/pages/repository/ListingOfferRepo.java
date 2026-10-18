package com.pages.repository;

import com.pages.model.ListingOffer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface ListingOfferRepo extends JpaRepository<ListingOffer,Long> {
    Optional<ListingOffer> findByListingIdAndBuyerIdAndExpiresAt(Long listingId, Long buyerId, Instant expiresAt);
}
