package com.pages.service;

import com.pages.enums.InventoryStatus;
import com.pages.enums.OrderStatus;
import com.pages.enums.PaymentStatus;
import com.pages.model.InventoryItem;
import com.pages.model.ListingOrder;
import com.pages.model.Payment;
import com.pages.repository.InventoryItemRepo;
import com.pages.repository.ListingOrderRepo;
import com.pages.repository.PaymentRepo;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@Slf4j
public class OrderCleanupService {


    private final InventoryItemRepo inventoryItemRepo;
    private final ListingOrderRepo listingOrderRepo;
    private final PaymentRepo paymentRepo;

    public OrderCleanupService(InventoryItemRepo inventoryItemRepo, ListingOrderRepo listingOrderRepo, PaymentRepo paymentRepo) {
        this.inventoryItemRepo = inventoryItemRepo;
        this.listingOrderRepo = listingOrderRepo;
        this.paymentRepo = paymentRepo;
    }

    // Runs once every 5 minutes (300,000 milliseconds)
    @Scheduled(fixedRate = 300000)
    @Transactional
    public void cleanupAbandonedReservations() {
        Instant now = Instant.now();
        log.info("Executing database sweep for expired checkout reservations at timestamp: {}", now);

        // 1. Find all physical items still locked past their 15-minute reservation deadline
        List<InventoryItem> expiredItems = inventoryItemRepo.findByStatusAndReservedUntilBefore(
                InventoryStatus.RESERVED, now
        );

        if (!expiredItems.isEmpty()) {
            log.info("Found {} ghost items to release back to storefront catalog.", expiredItems.size());

            for (InventoryItem item : expiredItems) {
                // Return item back to store catalog availability pools
                item.setStatus(InventoryStatus.AVAILABLE);
                item.setReservedUntil(null);
                inventoryItemRepo.save(item);
            }
        }

        // Find orders created more than 40 minutes ago that are still stuck as PENDING
        Instant orderTimeoutLimit = Instant.now().minusSeconds(2400); // 40 minutes
        List<ListingOrder> abandonedOrders = listingOrderRepo.findByOrderStatusAndCreatedAtBefore(
                OrderStatus.PROCESSING, orderTimeoutLimit
        );

        for (ListingOrder order : abandonedOrders) {
            order.setOrderStatus(OrderStatus.CANCELLED);
            listingOrderRepo.save(order);
            log.info("Marked abandoned order header {} as CANCELLED.", order.getOrderNumber());

            Payment payment =paymentRepo.findByListingOrderId(order.getId()).orElse(null);
            if(payment!=null){
                payment.setStatus(PaymentStatus.CANCELLED);
                paymentRepo.save(payment);
            }
        }


    }
}
