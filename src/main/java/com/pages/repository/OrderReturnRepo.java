package com.pages.repository;

import com.pages.model.ListingOrderItem;
import com.pages.model.OrderReturn;
import com.pages.model.ReturnShipment;
import com.pages.model.SellerProfile;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OrderReturnRepo extends JpaRepository<OrderReturn,Long> {

    Optional<OrderReturn> findByListingOrderItem(ListingOrderItem item);

    boolean existsByListingOrderItemListingOrderIdAndStatus(Long orderId, String status);



}
