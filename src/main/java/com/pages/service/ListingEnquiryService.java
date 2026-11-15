package com.pages.service;

import com.pages.dto.ListingUpdateResponse;
import com.pages.dto.ProductData;
import com.pages.exception.PhotoNotFoundException;
import com.pages.model.AppUser;
import com.pages.model.ListingEnquiry;
import com.pages.model.ListingTransaction;
import com.pages.model.SellerProfile;
import com.pages.repository.AppUserRepo;
import com.pages.repository.ListingEnquiryRepo;
import com.pages.repository.ListingTransactionRepo;
import com.pages.util.UtilService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ListingEnquiryService {

    private final ListingEnquiryRepo listingEnquiryRepo;
    private final ListingTransactionRepo listingTransactionRepo;
    private final MessageSource messageSource;
    private final AppUserRepo appUserRepo;
    private final EmailNotificationService emailNotificationService;
    private final ListingTransactionService listingTransactionService;


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

           String token = UtilService.generateToken();
           Instant expiresAt = Instant.now().plus(7, ChronoUnit.DAYS);
           ListingEnquiry listingEnquiry = ListingEnquiry.builder()
                   .buyer(appUser)
                   .listingTransaction(listingTransaction)
                   .question(enquiry)
                   .token(token)
                   .tokenExpiresAt(expiresAt)
                   .build();

           ListingEnquiry savedEnquiry =listingEnquiryRepo.save(listingEnquiry);
           //notifySeller
           String email =listingEnquiry.getListingTransaction().getInventory().getSeller().getUser().getUsername();
           String product = savedEnquiry.getListingTransaction().getInventory().getProductCatalog().getName();

           //add token to listing transaction for mark as sold validation
           listingTransaction.setConfirmationToken(token);
           listingTransaction.setConfirmationStatus(null);
           listingTransaction.setConfirmationTokenCreatedAt(Instant.now());
           listingTransactionRepo.save(listingTransaction);

          emailNotificationService.notifySellerToUpdateListing(email,product,enquiry,token);

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

        ListingTransaction listing = enquiries.get(0).getListingTransaction();

        String product = listing.getInventory()
                .getProductCatalog().getName();

        String slug = listing.getInventory()
                .getProductCatalog().getSlug();

        String productUrl = "/shop/listing/" + slug
                + "?listing=" + listing.getId();

        Instant notifiedAt = Instant.now();

        Map<Long, List<ListingEnquiry>> enquiriesByBuyer =
                enquiries.stream()
                        .collect(Collectors.groupingBy(
                                enquiry -> enquiry.getBuyer().getId()
                        ));

        for (List<ListingEnquiry> buyerEnquiries : enquiriesByBuyer.values()) {

            AppUser buyer = buyerEnquiries.get(0).getBuyer();

            try {
                emailNotificationService.notifyBuyerAfterSellerUpdate(
                        buyer.getUsername(),
                        product,
                        productUrl
                );

                buyerEnquiries.forEach(enquiry -> {
                    enquiry.setNotified(true);
                    enquiry.setNotifiedAt(notifiedAt);
                });

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

    public ListingUpdateResponse validateToken(String token) {

        if (!StringUtils.hasText(token)) {
            String message = messageSource.getMessage(
                    "api.listing.auth.required",
                    null,
                    LocaleContextHolder.getLocale()
            );

            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }

        ListingEnquiry enquiry = listingEnquiryRepo.findByToken(token)
                .orElse(null);

        boolean isValidated= enquiry != null
                && enquiry.getTokenExpiresAt() != null
                && !enquiry.getTokenExpiresAt().isBefore(Instant.now())
                && enquiry.getUsedAt() == null;
      if(isValidated){
          return ListingUpdateResponse.builder()
                  .listingId(enquiry.getListingTransaction().getId())
                  .validated(true)
                  .build();
      }
      return ListingUpdateResponse.builder()
              .listingId(null)
              .validated(false)
              .build();
    }


    @Transactional
    public boolean updateListingWithToken(String token, ProductData productData) {

        if (!StringUtils.hasText(token)) {
            String message = messageSource.getMessage(
                    "api.listing.auth.required",
                    null,
                    LocaleContextHolder.getLocale()
            );
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,message);
        }

        ListingEnquiry enquiry = listingEnquiryRepo.findByTokenForUpdate(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST));

        Instant now = Instant.now();

        if (enquiry.getTokenExpiresAt() == null
                || enquiry.getTokenExpiresAt().isBefore(now)
                || enquiry.getUsedAt() != null) {
            return false;
        }

        ListingTransaction existingListing = enquiry.getListingTransaction();

        // Ensure the submitted listing matches the enquiry.
        if (productData.getId() == null
                || !existingListing.getId().equals(productData.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        SellerProfile sellerProfile = existingListing.getInventory().getSeller();

        ListingTransaction listing =
                listingTransactionService.editSellerProduct(sellerProfile, productData);

        // Consume token after successful update.
        enquiry.setUsedAt(Instant.now());
        enquiry.setToken(null);
        enquiry.setTokenExpiresAt(null);

        listingEnquiryRepo.save(enquiry);

        // Notify buyers after the transaction commits.
        notifyEnquiringBuyers(listing.getId());

        return true;
    }



}
