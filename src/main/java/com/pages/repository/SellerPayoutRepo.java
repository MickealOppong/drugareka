package com.pages.repository;

import com.pages.enums.PayoutStatus;
import com.pages.model.AppUser;
import com.pages.model.ListingOrderItem;
import com.pages.model.SellerPayout;
import jakarta.persistence.LockModeType;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SellerPayoutRepo extends JpaRepository<SellerPayout,Long>, JpaSpecificationExecutor<SellerPayout> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<SellerPayout> findByListingOrderItemId(Long itemId);


    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
    SELECT p
    FROM SellerPayout p
    WHERE p.listingOrderItem.id = :itemId
      AND p.status = :status
""")
    Optional<SellerPayout> findByListingOrderItemIdAndPayoutStatusForUpdate(
            @Param("itemId") Long itemId,
            @Param("status") PayoutStatus status
    );


    Page<SellerPayout> findBySellerUserId(Long id, Pageable pageable);
   @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<SellerPayout> findByTransferId(String id);
    List<SellerPayout> findByStatus(PayoutStatus payoutStatus);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT p FROM SellerPayout p WHERE p.id = :id")
    Optional<SellerPayout> findByIdForUpdate(@Param("id") Long id);

    List<SellerPayout> findAllByStatus(PayoutStatus payoutStatus);

    boolean existsByListingOrderItemIdAndStatus(Long orderItemId,PayoutStatus payoutStatus);


}
