package com.pages.schedular;

import com.pages.repository.OrderDisputeRepo;
import com.pages.service.OrderDisputeService;
import com.pages.service.OrderReturnService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReturnShippingPaymentScheduler {

    private final OrderDisputeService orderDisputeService;

    /**
     * Reminds seller to pay for return shipping
     */
    @Scheduled(cron = "0 0 9,17 * * *", zone = "Europe/Warsaw")
    public void executePayout() {
      orderDisputeService.sendPaymentLinkToSellerForReturnShipment();
    }

}
