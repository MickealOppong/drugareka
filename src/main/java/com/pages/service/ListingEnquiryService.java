package com.pages.service;

import com.pages.model.AppUser;
import com.pages.model.ListingEnquiry;
import com.pages.model.ListingTransaction;
import com.pages.model.SellerProfile;
import com.pages.repository.AppUserRepo;
import com.pages.repository.ListingEnquiryRepo;
import com.pages.repository.ListingTransactionRepo;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ListingEnquiryService {

    private final ListingEnquiryRepo listingEnquiryRepo;
    private final ListingTransactionRepo listingTransactionRepo;
    private final MessageSource messageSource;
    private final AppUserRepo appUserRepo;
    private final EmailNotificationService emailNotificationService;


    @Transactional
    public boolean addBuyerEnquiry(Jwt jwt,Long listingId, String enquiry){

       try{
           if(jwt==null){
               String message =messageSource.getMessage("api.listing.enquiry.error",null, LocaleContextHolder.getLocale());
               throw new ResponseStatusException(HttpStatus.BAD_REQUEST,message);
           }

           ListingTransaction listingTransaction = listingTransactionRepo.findById(listingId).orElse(null);
           if(listingTransaction==null){
               String message =messageSource.getMessage("api.listing.enquiry.error",null, LocaleContextHolder.getLocale());
               throw new ResponseStatusException(HttpStatus.BAD_REQUEST,message);
           }


           AppUser appUser = appUserRepo.findByUsername(jwt.getSubject()).orElse(null);
           if(appUser==null){
               String message =messageSource.getMessage("api.listing.enquiry.error",null, LocaleContextHolder.getLocale());
               throw new ResponseStatusException(HttpStatus.BAD_REQUEST,message);
           }
           ListingEnquiry listingEnquiry = ListingEnquiry.builder()
                   .buyer(appUser)
                   .listingTransaction(listingTransaction)
                   .question(enquiry)
                   .build();

           ListingEnquiry savedEnquiry =listingEnquiryRepo.save(listingEnquiry);
           //notifySeller
           String email =listingEnquiry.getListingTransaction().getInventory().getSeller().getUser().getUsername();
           String product = savedEnquiry.getListingTransaction().getInventory().getProductCatalog().getName();

           emailNotificationService.notifySellerToUpdateListing(email,product,enquiry);
           return true;
       }catch (Exception e){
           log.error("Failed to send enquiry:{}",e.getMessage());
           return false;
       }

    }


    @Transactional
    public void notifyEnquiringBuyers(Long listingId) {

        List<ListingEnquiry> enquiries =
                listingEnquiryRepo
                        .findByListingTransactionIdAndNotifiedFalse(listingId);

        if (enquiries.isEmpty()) {
            return;
        }

        Instant notifiedAt = Instant.now();

        for (ListingEnquiry enquiry : enquiries) {

            AppUser buyer = enquiry.getBuyer();

            try {
                // Send buyer notification
                String email = buyer.getUsername();
                String product = enquiry.getListingTransaction().getInventory().getProductCatalog().getName();
                String slug = enquiry.getListingTransaction().getInventory().getProductCatalog().getSlug();
                String productUrl = "/shop/listing/"+slug+"?listing="+enquiry.getListingTransaction().getId();
               emailNotificationService.notifyBuyerAfterSellerUpdate(
                        email,
                        product,
                        productUrl
                );

                enquiry.setNotified(true);
                enquiry.setNotifiedAt(notifiedAt);

            } catch (Exception e) {
                log.error(
                        "Failed to notify buyer {} about listing {}",
                        buyer.getId(),
                        listingId,
                        e
                );
            }
        }

        listingEnquiryRepo.saveAll(enquiries);
    }

}
