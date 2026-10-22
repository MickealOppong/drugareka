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
    private final ListingOrderItemRepo listingOrderItemRepo;
    private final PaymentRepo paymentRepo;

    public OrderCleanupService(InventoryItemRepo inventoryItemRepo, ListingOrderRepo listingOrderRepo, ListingOrderItemRepo listingOrderItemRepo, PaymentRepo paymentRepo) {
        this.inventoryItemRepo = inventoryItemRepo;
        this.listingOrderRepo = listingOrderRepo;
        this.listingOrderItemRepo = listingOrderItemRepo;
        this.paymentRepo = paymentRepo;
    }

    /*
    // Runs once every 5 minutes (300,000 milliseconds)
    @Scheduled(cron = "0 0/30 * * * *", zone = "Europe/Warsaw")
    @Transactional
    public void cleanupAbandonedReservations() {
        Instant now = Instant.now();
        log.info("Executing database sweep for expired checkout reservations at timestamp: {}", now);


        // Find orders created more than 45 minutes ago that are still stuck as PENDING
        Instant orderTimeoutLimit = Instant.now().minusSeconds(2700); // 45 minutes
        List<ListingOrder> abandonedOrders = listingOrderRepo.findByOrderStatusAndCreatedAtBeforeForUpdate(
                OrderStatus.PROCESSING, orderTimeoutLimit
        );

        for (ListingOrder order : abandonedOrders) {
            order.setOrderStatus(OrderStatus.CANCELLED);
            listingOrderRepo.save(order);
            log.info("Marked abandoned order header {} as CANCELLED.", order.getOrderNumber());


            order.getItems().forEach(lineItem->{
                lineItem.setOrderItemStatus(OrderItemStatus.CANCELLED);

                listingOrderItemRepo.save(lineItem);

                //inventory release
                InventoryItem inventoryItem = lineItem.getInventoryItem();
                if(inventoryItem.getReservedUntil() !=null && inventoryItem.getReservedUntil().isBefore(Instant.now())){
                    inventoryItem.setStatus(InventoryStatus.AVAILABLE);
                    inventoryItem.setReservedUntil(null);
                    inventoryItem.setReservedBy(null);
                    inventoryItemRepo.save(inventoryItem);
                }

            });

            Payment payment =paymentRepo.findByListingOrderIdAndStatus(order.getId(),PaymentStatus.PENDING).orElse(null);
            if(payment!=null){
                payment.setStatus(PaymentStatus.CANCELLED);
                paymentRepo.save(payment);
            }
        }


    }

     */
}
