package com.pages.schedular;

import com.pages.service.PaymentService;
import com.pages.service.PayoutProcessingService;
import com.pages.service.StripePaymentProviderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PayoutScheduler {

    private final PayoutProcessingService payoutProcessingService;


    /**
     * Process eligible seller payouts.
     * Runs again 2 minutes after the previous execution finishes.
     */
  // @Scheduled(fixedDelay = 120_000)
    @Scheduled(cron = "0 0 11,17 * * *", zone = "Europe/Warsaw")
    public void executePayout() {
        log.info("Payout scheduler started");

        try {

            payoutProcessingService.confirmReceiptAndReleaseFunds();
            log.info("Payout scheduler finished");
        } catch (Exception e) {
            log.error("Payout scheduler execution failed", e);
        }
    }
}