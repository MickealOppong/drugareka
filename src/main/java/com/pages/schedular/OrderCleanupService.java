package com.pages.schedular;

import com.pages.service.ListingOrderService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrderCleanupService {


    private final ListingOrderService listingOrderService;


    // Runs once every 5 minutes (300,000 milliseconds)
   // @Scheduled(fixedDelay = 120_000)
    @Scheduled(cron = "0 0 11,16 * * *", zone = "Europe/Warsaw")
    @Transactional
    public void handleUnconfirmedPaidOrdersForPayout() {

        log.info("Order confirmation scheduler started");

        try {
            //checks for unConfirmed deliveries after 24 hours and confirm them
            listingOrderService.validateItemDeliveryAndApprovePayout();
            log.info("Order confirmation scheduler finished");
        } catch (Exception e) {
            log.error("Order confirmation scheduler execution failed", e);
        }

    }


    // Runs once every 5 minutes (300,000 milliseconds)
   // @Scheduled(fixedDelay = 120_000)
    @Primary
    @Scheduled(cron = "0 0 9,17 * * *", zone = "Europe/Warsaw")
    @Transactional
    public void handleUnconfirmedPaidOrdersShipmentForRefund() {

        log.info("Shipment confirmation scheduler started");

        try {
            //checks for unconfirmed orders by seller and cancels them for refund
            listingOrderService.validateSellerUnConfirmedOrderForRefund();
            log.info("Shipment confirmation scheduler finished");
        } catch (Exception e) {
            log.error("Shipment confirmation scheduler execution failed", e);
        }

    }


    // Runs once every 5 minutes (300,000 milliseconds)
    @Scheduled(fixedDelay = 120_000)
    @Transactional
    public void cleanupAbandonedReservations() {

        log.info("Order cleanup scheduler started");

        try {
            //checks for any order pending payment after few hours and cancels it, this mostly abandon checkouts
            listingOrderService.cleanUpAbandonOrders();
            log.info("Order cleanup scheduler finished");
        } catch (Exception e) {
            log.error("Order cleanup scheduler execution failed", e);
        }

    }




}
