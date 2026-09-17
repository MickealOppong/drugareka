package com.pages.repository;

import com.pages.enums.OrderStatus;
import com.pages.model.ListingOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.jpa.repository.JpaRepository;
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

    List<ListingOrder> findByOrderStatusAndCreatedAtBefore(OrderStatus status, Instant time);
    Long countByBuyerIdAndOrderStatus(Long buyerId,OrderStatus orderStatus);
}
