package com.pages.repository;

import com.pages.enums.OrderStatus;
import com.pages.model.ListingOrder;
import com.pages.model.Payment;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface ListingOrderRepo extends JpaRepository<ListingOrder,Long> {


   Page<ListingOrder> findByBuyerId(Long buyerId, Pageable pageable);
    List<ListingOrder> findAllById(Long buyerId);

    Optional<ListingOrder> findByOrderNumber(String orderNumber);

    Long countByBuyerIdAndOrderStatus(Long buyerId,OrderStatus orderStatus);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("""
       SELECT o
       FROM ListingOrder o
       WHERE o.id = :orderId
       """)
    Optional<ListingOrder> findByIdForUpdate(
            @Param("orderId") Long orderId
    );

 @Lock(LockModeType.PESSIMISTIC_WRITE)
 @org.springframework.data.jpa.repository.Query(value = """
    SELECT o
    FROM ListingOrder o
    WHERE o.orderStatus = :status
      AND o.createdAt < :time
    """)
 List<ListingOrder> findByOrderStatusAndCreatedAtBeforeForUpdate(
         @Param("status") OrderStatus status,
         @Param("time") Instant time
 );
}
