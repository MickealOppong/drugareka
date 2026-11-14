package com.pages.repository;

import com.pages.model.ListingEnquiry;
import com.pages.model.SellerProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ListingEnquiryRepo extends JpaRepository<ListingEnquiry,Long> {

    List<ListingEnquiry> findByListingTransactionIdAndNotifiedFalse(Long id);

}
