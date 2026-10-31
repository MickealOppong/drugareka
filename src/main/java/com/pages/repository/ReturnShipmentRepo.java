package com.pages.repository;

import com.pages.model.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ReturnShipmentRepo extends JpaRepository<ReturnShipment,Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
    SELECT rs
    FROM ReturnShipment rs
    WHERE rs.receiptConfirmationToken = :token
""")
    Optional<ReturnShipment> findByReceiptConfirmationTokenForUpdate(
            @Param("token") String token
    );
    Optional<ReturnShipment> findByReceiptConfirmationToken( @Param("token") String token);
    Optional<ReturnShipment> findByOrderDispute(OrderDispute dispute);
    Page<ReturnShipment> findByOrderDisputeListingOrderItemListingOrderBuyer(AppUser buyer, Pageable pageable);

}
