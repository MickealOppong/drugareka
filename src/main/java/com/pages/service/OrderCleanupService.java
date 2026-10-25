package com.pages.service;

import com.pages.enums.InventoryStatus;
import com.pages.enums.OrderItemStatus;
import com.pages.enums.OrderStatus;
import com.pages.enums.PaymentStatus;
import com.pages.model.InventoryItem;
import com.pages.model.ListingOrder;
import com.pages.model.Payment;
import com.pages.repository.InventoryItemRepo;
import com.pages.repository.ListingOrderItemRepo;
import com.pages.repository.ListingOrderRepo;
import com.pages.repository.PaymentRepo;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrderCleanupService {


    private final ListingOrderService listingOrderService;



    // Runs once every 5 minutes (300,000 milliseconds)
    @Scheduled(fixedDelay = 120_000)
    @Transactional
    public void handleUnconfirmedPaidOrdersForPayout() {

        log.info("Order confirmation scheduler started");

        try {
            listingOrderService.validateItemDeliveryAndApprovePayout();
            log.info("Order confirmation scheduler finished");
        } catch (Exception e) {
            log.error("Order confirmation scheduler execution failed", e);
        }

    }


    // Runs once every 5 minutes (300,000 milliseconds)
    @Scheduled(fixedDelay = 120_000)
    @Transactional
    public void cleanupAbandonedReservations() {

        log.info("Order cleanup scheduler started");

        try {
            listingOrderService.cleanUpAbandonOrders();
            log.info("Order cleanup scheduler finished");
        } catch (Exception e) {
            log.error("Order cleanup scheduler execution failed", e);
        }

    }




}
