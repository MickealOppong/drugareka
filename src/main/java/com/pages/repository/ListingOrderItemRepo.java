package com.pages.repository;

import com.pages.enums.InventoryStatus;
import com.pages.enums.OrderStatus;
import com.pages.model.ListingOrderItem;
import com.pages.model.ListingTransaction;
import com.pages.model.SellerProfile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface ListingOrderItemRepo extends JpaRepository<ListingOrderItem,Long> {

    List<ListingOrderItem> findByListingOrderId(Long orderId);
    Page<ListingOrderItem> findBySellerId(Long sellerId,Pageable pageable);
    Long countBySellerAndInventoryItemStatus(SellerProfile seller, InventoryStatus inventoryStatus);
    Long countBySellerIdAndListingOrderOrderStatus(Long userId, OrderStatus orderStatus);
    Long countBySellerId(Long userId);
    Optional<ListingOrderItem> findFirstBySellerIdAndInventoryItemStatusOrderByCreatedAtDesc(Long sellerId, InventoryStatus inventoryStatus);
    Optional<ListingOrderItem> findByReceiptConfirmationToken(String token);

    @Query("""
        SELECT item FROM ListingOrderItem item 
        WHERE item.orderItemStatus = com.pages.enums.OrderItemStatus.PAID 
        AND item.receiptConfirmationTokenExpiresAt < :now 
        AND item.receiptConfirmedAt IS NULL
    """)
    List<ListingOrderItem> findAllPaidAndExpiredReceiptTokens(@Param("now") Instant now);
}
