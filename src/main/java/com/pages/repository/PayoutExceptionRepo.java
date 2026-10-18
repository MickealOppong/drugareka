package com.pages.repository;

import com.pages.model.PayoutException;
import com.pages.model.SellerPayout;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PayoutExceptionRepo extends JpaRepository<PayoutException,Long> {
    Optional<PayoutException> findBySellerPayout(SellerPayout sellerPayout);
}
