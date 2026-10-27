package com.pages.repository;

import com.pages.enums.InventoryStatus;
import com.pages.enums.ShipmentStatus;
import com.pages.model.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface SellerShipmentRepo extends JpaRepository<SellerShipment,Long> {

    List<SellerShipment> findBySellerAndListingOrderItemListingOrder(SellerProfile seller, ListingOrder order);

    Page<SellerShipment> findBySeller(SellerProfile sellerProfile, Pageable pageable);
    List<SellerShipment> findBySellerShipmentTokenToken(String token);
    List<SellerShipment> findBySellerShipmentToken(SellerShipmentToken token);
    List<SellerShipment> findBySeller(SellerProfile sellerProfile);
    Optional<SellerShipment> findByListingOrderItemId(Long id);
    Optional<SellerShipment> findFirstBySellerIdOrderByCreatedAtDesc(Long sellerId);
    Optional<SellerShipment> findByListingOrderItemListingOrderOrderNumber(String orderNumber);
    List<SellerShipment> findBySellerShipmentTokenAndShipmentStatus(SellerShipmentToken token,ShipmentStatus shipmentStatus);


    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("""
    SELECT s
    FROM SellerShipment s
    JOIN s.sellerShipmentToken t
    WHERE t.shipmentConfirmationStatus IS NULL
    AND t.expiresAt <=:now
    """)
    List<SellerShipment> findAllUnconfirmedShipmentForUpdate(@Param("now") Instant now);
}
