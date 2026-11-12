package com.pages.schedular;

import com.pages.service.ListingConfirmationService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ListingConfirmationScheduler {

    private final ListingConfirmationService listingConfirmationService;

    // Check for listings requiring confirmation every day.
    @Scheduled(cron = "0 0 2 * * *")
    public void checkForAvailability() {
        listingConfirmationService.checkForAvailability();
    }


    // Expire unanswered confirmation requests every hour.
    @Scheduled(cron = "0 0 * * * *")
    public void expirePendingConfirmations() {
        listingConfirmationService.expirePendingConfirmations();
    }



}
