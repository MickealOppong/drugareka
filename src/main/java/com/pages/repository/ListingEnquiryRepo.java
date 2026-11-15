package com.pages.repository;

import com.pages.model.ListingEnquiry;
import com.pages.model.SellerProfile;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
@Repository
public interface ListingEnquiryRepo extends JpaRepository<ListingEnquiry,Long> {

    List<ListingEnquiry> findByListingTransactionIdAndNotifiedFalse(Long id);
    Optional<ListingEnquiry> findByToken(String token);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("""
       SELECT e
       FROM ListingEnquiry e
       WHERE e.token = :token
       """)
    Optional<ListingEnquiry> findByTokenForUpdate(String token);

}
