
package com.pages.service;

import com.pages.enums.InventoryStatus;
import com.pages.enums.ListingStatus;
import com.pages.model.ListingTransaction;
import com.pages.repository.ListingTransactionRepo;
import com.pages.util.UtilService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ListingConfirmationService {

    private static final int LISTING_AGE_DAYS = 14;
    private static final int RESPONSE_WINDOW_HOURS = 24;

    private final ListingTransactionRepo listingTransactionRepo;
    private final EmailNotificationService emailNotificationService;

    @Value("${app.frontend-url}")
    private String baseUrl;

    /**
     * Seller confirms that the item is still available.
     */
    public boolean confirm(String token) {

        Optional<ListingTransaction> result =
                listingTransactionRepo.findByConfirmationToken(token);

        if (result.isEmpty()) {
            log.warn("Availability confirmation token not found.");
            return false;
        }

        ListingTransaction listing = result.get();


        if (listing.getConfirmationStatus()!=null && listing.getConfirmationStatus().equalsIgnoreCase("CONFIRMED")) {
            return true;
        }

        if (isTokenExpired(listing)) {
            expireConfirmation(listing);
            return false;
        }

        if (listing.getInventory().getStatus() != InventoryStatus.AVAILABLE) {
            return false;
        }


        listing.setListingStatus(ListingStatus.PUBLISHED);
        listing.setConfirmationStatus("CONFIRMED");

        listingTransactionRepo.save(listing);

        log.info("Listing {} confirmed as available.", listing.getId());

        return true;
    }

    /**
     * Seller confirms that the item has already been sold.
     */
    public boolean markAsSold(String token) {

        Optional<ListingTransaction> result =
                listingTransactionRepo.findByConfirmationToken(token);

        if (result.isEmpty()) {
            log.warn("Availability confirmation token not found.");
            return false;
        }

        ListingTransaction listing = result.get();


        if (listing.getConfirmationStatus()!=null && listing.getConfirmationStatus().equalsIgnoreCase("SOLD")) {
            return true;
        }

        if (isTokenExpired(listing)) {
            expireConfirmation(listing);
            return false;
        }

        listing.setListingStatus(ListingStatus.DRAFT);
        listing.setConfirmationStatus("SOLD");

        listingTransactionRepo.save(listing);

        log.info("Listing {} marked as sold by seller and no longer available.", listing.getId());

        return true;
    }

    /**
     * Finds available listings older than 14 days and send
     * an availability confirmation email to their sellers.
     */
    public void checkForAvailability() {

        Instant cutoff = Instant.now().minus(LISTING_AGE_DAYS, ChronoUnit.DAYS);

        List<ListingTransaction> listings =
                listingTransactionRepo
                        .findAllByInventoryStatusAndListingStatusAndCreatedAtBefore(
                                InventoryStatus.AVAILABLE,
                                ListingStatus.PUBLISHED,
                                cutoff
                        );

        for (ListingTransaction listing : listings) {

            try {
                String token = UtilService.generateListingConfirmationToken();

                // Hide the listing while waiting for seller confirmation.
                //listing.setListingStatus(ListingStatus.DRAFT);
                listing.setConfirmationToken(token);
                listing.setConfirmationStatus(null);
                listing.setConfirmationTokenCreatedAt(Instant.now());

            listingTransactionRepo.save(listing);

                String email = listing.getInventory()
                        .getSeller()
                        .getUser()
                        .getUsername();

                String product = listing.getInventory()
                        .getProductCatalog()
                        .getName();

                emailNotificationService.checkForAvailabilityWithSeller(
                        email,
                        product,
                        baseUrl,
                  token
                );

                log.info(
                        "Availability confirmation email sent for listing {}.",
                        listing.getId()
                );

            } catch (Exception e) {
                log.error(
                        "Could not send availability confirmation for listing {}.",
                        listing.getId(),
                        e
                );
            }
        }
    }


    /**
     * Expires confirmation requests that have not received
     * a response within 24 hours.
     *
     * The listing remains a draft and is not visible to buyers.
     */
    public void expirePendingConfirmations() {

        Instant expiryTime = Instant.now()
                .minus(RESPONSE_WINDOW_HOURS, ChronoUnit.HOURS);

        List<ListingTransaction> expiredListings =
                listingTransactionRepo
                        .findAllByConfirmationTokenIsNotNullAndConfirmationTokenCreatedAtBefore(
                                expiryTime
                        );

        for (ListingTransaction listing : expiredListings) {

            // Do not republish listings when the seller has not responded.
            listing.setListingStatus(ListingStatus.DRAFT);

            listingTransactionRepo.save(listing);

            log.info(
                    "Availability confirmation expired for listing {}.",
                    listing.getId()
            );
        }
    }

    private boolean isTokenExpired(ListingTransaction listing) {

        if (listing.getConfirmationTokenCreatedAt() == null) {
            return true;
        }

        Instant expiryTime = listing.getConfirmationTokenCreatedAt()
                .plus(RESPONSE_WINDOW_HOURS, ChronoUnit.HOURS);

        return Instant.now().isAfter(expiryTime);
    }

    private void expireConfirmation(ListingTransaction listing) {

        listing.setListingStatus(ListingStatus.DRAFT);

        listingTransactionRepo.save(listing);
    }

}
