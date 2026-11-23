package com.pages.schedular;

import com.pages.enums.OrderItemStatus;
import com.pages.enums.OrderStatus;
import com.pages.enums.SellerStatus;
import com.pages.enums.ShipmentStatus;
import com.pages.model.*;
import com.pages.repository.ListingOrderItemRepo;
import com.pages.repository.SellerShipmentRepo;
import com.pages.service.EmailNotificationService;
import com.pages.service.SellerProfileService;
import com.pages.util.UtilService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AutomaticOrderConfirmationScheduler {

    private final ListingOrderItemRepo orderItemRepo;
    private final SellerShipmentRepo sellerShipmentRepo;
    private final EmailNotificationService emailNotificationService;

    /**
     *  BACKEND AUTOMATION ORDER CONFIRMATION IF BUYER DOES NOT CONFIRM
     * Patterns: "0 0 * * * *" (Executes every hour on the hour).
     * Automatically completes fulfillment and force-releases seller payments for expired tokens.
     */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void autoConfirmExpiredReceiptTokens() {
        log.info("Fulfillment Engine: Scanning database tracks for expired buyer confirmation timelines...");

        Instant now = Instant.now();

        // Step 1: Pull out all items matching the target validation criteria
        List<ListingOrderItem> expiredItems = orderItemRepo.findAllPaidAndExpiredReceiptTokensForUpdate(now,OrderItemStatus.PAID);

        if (expiredItems.isEmpty()) {
            log.info("Fulfillment Engine: Zero expired confirmation states detected. Standing down.");
            return;
        }

        log.warn("Fulfillment Engine: Found {} items with expired confirmation windows. Initializing auto-approval force payout...",
                expiredItems.size());

        for (ListingOrderItem item : expiredItems) {

           SellerShipment itemShipment= sellerShipmentRepo.findByListingOrderItemId(item.getId()).orElse(null);

           if(itemShipment!=null && itemShipment.getShipmentStatus().equals(ShipmentStatus.DELIVERED)){
               // Step 2: Simulate manual buyer clearance by recording confirmation timestamps and updating states
               item.setReceiptConfirmedAt(now);
               orderItemRepo.save(item);
           }

            log.info("Fulfillment Engine: Forced auto-receipt confirmation for Item ID: {} | Connected Order ID: {}",
                    item.getId(), item.getListingOrder().getId());
        }
    }

    /**
     *  BACKEND AUTOMATION REMINDER TO BUYER 12 HOURS BEFORE CONFIRMATION WINDOW CLOSES
     * Patterns: "0 0 * * * *" (Executes every hour on the hour).
     * Automatically completes fulfillment and force-releases seller payments for expired tokens.
     */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void receiptConfirmationReminderToBuyer() {

        log.info("Fulfillment Engine: Scanning for pending receipt confirmation reminders...");

        Instant now = Instant.now();
        Instant expiresIn12Hours = now.plus(12, ChronoUnit.HOURS);

        List<ListingOrderItem> pendingItems =
                orderItemRepo.findAllPendingReceiptConfirmationRemindersForUpdate(
                        now,
                        expiresIn12Hours,
                        OrderItemStatus.PAID
                );

        if (pendingItems.isEmpty()) {
            log.info("Fulfillment Engine: No receipt confirmation reminders required.");
            return;
        }

        for (ListingOrderItem item : pendingItems) {

            String token = item.getReceiptConfirmationToken();

            item.setReceiptConfirmationReminderSentAt(now);

            ListingOrder order = item.getListingOrder();

            String email = order.getBuyer().getUsername();
            String product = item.getProductNameSnapshot();
            String name = order.getBuyerNameSnapshot();
            String orderNumber = order.getOrderNumber();

           orderItemRepo.save(item);

            emailNotificationService.remindBuyerOfPendingConfirmation(
                    email,
                    name,
                    product,
                    orderNumber,
                    token
            );

            log.info(
                    "Fulfillment Engine: Receipt confirmation reminder queued "
                            + "for Item ID: {} | Order ID: {}",
                    item.getId(),
                    order.getId()
            );
        }
    }

/**
 * Sends a reminder to sellers who have not confirmed
 * their orders and whose confirmation window expires
 * within the next 12 hours.
 * Executes every hour.
 */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void receiptConfirmationReminderToSeller() {

        log.info("Fulfillment Engine: Scanning for pending seller confirmation reminders...");

        Instant now = Instant.now();
        Instant expiresIn12Hours = now.plus(12, ChronoUnit.HOURS);

        List<SellerShipment> shipments =
                sellerShipmentRepo.findAllUnconfirmedShipmentForUpdate(
                        now,
                        expiresIn12Hours
                );

        if (shipments.isEmpty()) {
            log.info("Fulfillment Engine: No seller confirmation reminders required.");
            return;
        }

        for (SellerShipment shipment : shipments) {

            SellerShipmentToken shipmentToken =
                    shipment.getSellerShipmentToken();

            if (shipmentToken.getReceiptConfirmationReminderSentAt() != null) {
                continue;
            }

            ListingOrderItem orderItem = shipment.getListingOrderItem();
            ListingOrder order = orderItem.getListingOrder();

            String token = shipmentToken.getToken();
            String email = orderItem.getSeller().getUser().getUsername();
            String product = orderItem.getProductNameSnapshot();
            String sellerName = orderItem.getSellerNameSnapshot();
            String orderNumber = order.getOrderNumber();

            Long shipmentId = shipment.getId();
            Long orderId = order.getId();

            shipmentToken.setReceiptConfirmationReminderSentAt(now);

          sellerShipmentRepo.save(shipment);

            emailNotificationService.remindSellerOfPendingConfirmation(
                    email,
                    sellerName,
                    product,
                    orderNumber,
                    token
            );

            log.info(
                    "Fulfillment Engine: Seller confirmation reminder queued "
                            + "for Shipment ID: {} | Order ID: {}",
                    shipmentId,
                    orderId
            );
        }
    }

}
