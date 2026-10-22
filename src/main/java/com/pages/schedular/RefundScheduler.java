package com.pages.schedular;

import com.pages.enums.PaymentProvider;
import com.pages.enums.PaymentStatus;
import com.pages.model.Payment;
import com.pages.service.PaymentService;
import com.pages.service.PayoutProcessingService;
import com.stripe.exception.StripeException;
import com.stripe.model.Refund;
import com.stripe.param.RefundCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class RefundScheduler {


private final PaymentService paymentService;
    /**
     * Process eligible seller payouts.
     * Runs again 2 minutes after the previous execution finishes.
     */
    @Primary
    //@Scheduled(cron = "0 0 9,17 * * *", zone = "Europe/Warsaw")
    @Scheduled(fixedDelay = 120_000)
    public void executePayout() {
        log.info("Refund scheduler started");

        try {
                paymentService.executeStripeRefund();
            log.info("Refund scheduler finished");
        } catch (Exception e) {
            log.error("Refund scheduler execution failed", e);
        }
    }




}
