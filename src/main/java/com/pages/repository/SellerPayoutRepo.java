package com.pages.repository;

import com.pages.enums.PayoutStatus;
import com.pages.model.AppUser;
import com.pages.model.SellerPayout;
import jakarta.persistence.LockModeType;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SellerPayoutRepo extends JpaRepository<SellerPayout,Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<SellerPayout> findByListingOrderItemId(Long itemId);
    Page<SellerPayout> findBySellerUserId(Long id, Pageable pageable);
   @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<SellerPayout> findByTransferId(String id);
    List<SellerPayout> findByStatus(PayoutStatus payoutStatus);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT p FROM SellerPayout p WHERE p.id = :id")
    Optional<SellerPayout> findByIdForUpdate(@Param("id") Long id);
}
