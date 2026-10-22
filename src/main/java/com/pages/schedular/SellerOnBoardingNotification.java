package com.pages.schedular;

import com.pages.model.SellerProfile;
import com.pages.repository.SellerProfileRepo;
import com.pages.service.EmailNotificationService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SellerOnBoardingNotification {


    private String url ="https://localhost:5173/account/me";
    private final SellerProfileRepo sellerProfileRepo;
    private final EmailNotificationService emailNotificationService;
    /**
     * Process eligible seller payouts.
     * Runs again 2 minutes after the previous execution finishes.
     */

    @Scheduled(cron = "0 0 9,17 * * *", zone = "Europe/Warsaw")
   // @Scheduled(fixedDelay = 120_000)
    @Transactional
    public void executePayout() {
        log.info("Notification scheduler started");

        try {
            int counter = 0;
           for(SellerProfile profile:sellerProfileRepo.findByStripeAccountIdIsNull()){
               String email = profile.getUser().getUsername();
               String name = profile.getFullLegalName();
               counter++;
               emailNotificationService.notifySellerToStartVerification(email,name,url);
           }
            log.info("Scheduler sent: {} sellers notification ot start stripe  onboarding",counter);
        } catch (Exception e) {
            log.error("Notification scheduler execution failed", e);
        }
    }
}
