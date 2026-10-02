package com.pages.repository;

import com.pages.model.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ReturnShipmentRepo extends JpaRepository<ReturnShipment,Long> {
    Optional<ReturnShipment> findByToken(String token);
    Page<ReturnShipment> findByOrderReturnListingOrderItemListingOrderBuyer(AppUser buyer, Pageable pageable);
}
