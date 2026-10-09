package com.pages.scheduler;

import com.pages.enums.OrderItemStatus;
import com.pages.enums.ShipmentStatus;
import com.pages.model.ListingOrderItem;
import com.pages.model.SellerShipment;
import com.pages.repository.ListingOrderItemRepo;
import com.pages.repository.SellerShipmentRepo;
import com.pages.service.SellerProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AutomaticOrderConfirmationScheduler {

    private final ListingOrderItemRepo orderItemRepo;
    private final SellerShipmentRepo sellerShipmentRepo;

    /**
     *  BACKEND AUTOMATION ORDER CONFIRMATION IF BUYER DOES NOT IN 3 DAYS TASK
     * Patterns: "0 0 * * * *" (Executes every hour on the hour).
     * Automatically completes fulfillment and force-releases seller payments for expired tokens.
     */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void autoConfirmExpiredReceiptTokens() {
        log.info("Fulfillment Engine: Scanning database tracks for expired buyer confirmation timelines...");

        Instant now = Instant.now();

        // Step 1: Pull out all items matching the target validation criteria
        List<ListingOrderItem> expiredItems = orderItemRepo.findAllPaidAndExpiredReceiptTokens(now);

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

}
