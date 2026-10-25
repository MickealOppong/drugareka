package com.pages.repository;

import com.pages.model.ListingOrderItem;
import com.pages.model.OrderDispute;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OrderDisputeRepo extends JpaRepository<OrderDispute,Long> {

    Optional<OrderDispute> findByListingOrderItem(ListingOrderItem item);

    boolean existsByListingOrderItemListingOrderIdAndDisputeStatus(Long orderId, String status);

    Optional<OrderDispute> findByOrderNumber(String orderNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("""
       SELECT od
       FROM OrderDispute od
       WHERE od.orderNumber = :orderNumber
       """)
    Optional<OrderDispute> findByOrderNumberForUpdate(String orderNumber);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("""
       SELECT od
       FROM OrderDispute od
       WHERE od.id = :id
       """)
    Optional<OrderDispute> findByIdForUpdate(Long id);
}
