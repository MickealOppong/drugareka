package com.pages.repository;

import com.pages.enums.ShipmentStatus;
import com.pages.model.ListingOrder;
import com.pages.model.SellerProfile;
import com.pages.model.SellerShipment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SellerShipmentRepo extends JpaRepository<SellerShipment,Long> {

    Page<SellerShipment> findBySellerAndShipmentStatus(SellerProfile sellerProfile, ShipmentStatus shipmentStatus, Pageable pageable);
    List<SellerShipment> findByListingOrderItem(ListingOrder listingOrder);

    Optional<SellerShipment> findByListingOrderItemId(Long id);
}
