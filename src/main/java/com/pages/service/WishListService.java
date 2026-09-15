package com.pages.service;

import com.pages.dto.MediaResponse;
import com.pages.dto.ResponseDto;
import com.pages.dto.WishListResponse;
import com.pages.enums.InventoryStatus;
import com.pages.enums.ListingStatus;
import com.pages.exception.EntityNotFoundException;
import com.pages.exception.InvalidInputException;
import com.pages.model.*;
import com.pages.repository.InventoryItemPriceRepo;
import com.pages.repository.WishListRepo;
import com.pages.util.Media;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
@Slf4j
@Service
public class WishListService {

    private final WishListRepo wishListRepo;
    private final AppUserDetailsService appUserDetailsService;
    private final InventoryItemService inventoryItemService;
    private final ListingTransactionService listingTransactionService;
    private final MediaService mediaService;
    private final InventoryItemPriceService inventoryItemPriceService;

    public WishListService(WishListRepo wishListRepo, AppUserDetailsService appUserDetailsService, InventoryItemService inventoryItemService, ListingTransactionService listingTransactionService, MediaService mediaService, InventoryItemPriceService inventoryItemPriceService) {
        this.wishListRepo = wishListRepo;
        this.appUserDetailsService = appUserDetailsService;
        this.inventoryItemService = inventoryItemService;
        this.listingTransactionService = listingTransactionService;
        this.mediaService = mediaService;
        this.inventoryItemPriceService = inventoryItemPriceService;
    }

@Transactional
    public ResponseDto<Object> toggleWishList(Long listingId, Jwt jwt){
            try {

                //USER PROFILE
                String username = jwt.getSubject();
                AppUser currentUser = appUserDetailsService.getAppUserByUsername(username);

                //LISTING ITEM
                ListingTransaction listing= listingTransactionService.getListingTransaction(listingId,ListingStatus.PUBLISHED);

                if(listing==null || currentUser ==null){
                    throw  new EntityNotFoundException("Item or user does not exist");
                }

                Long currentUserId = currentUser.getId();
                Long sellerUserId = listing.getInventory().getSeller().getId();

                if(currentUserId.equals(sellerUserId)){
                    throw new IllegalStateException("You cannot add your own product to wishlist");
                }


                //FIND OR CREATE WISHLIST FOR USER
                Optional<WishList> existingWishlist = wishListRepo.findByListingIdAndUserId(listing.getId(), currentUserId);


                if (existingWishlist.isPresent()) {
                    log.info("{}",existingWishlist.get().getListingId());
                    wishListRepo.delete(existingWishlist.get());
                    return null;
                } else {

                    WishList newWishList = WishList.builder()
                            .userId(currentUserId)
                            .listingId(listing.getId())
                            .build();

                   WishList savedItem= wishListRepo.save(newWishList);
                    return ResponseDto.builder()
                            .data(false)
                            .httpStatus(HttpStatus.BAD_REQUEST.value())
                            .message("Error adding item to wishlist")
                            .build();
                }

            }catch (Exception e){
                return ResponseDto.builder()
                        .httpStatus(HttpStatus.BAD_REQUEST.value())
                        .message(e.getMessage())
                        .data(false)
                        .build();
            }
    }

    @Transactional(readOnly = true)
    public List<WishListResponse> allUserWishList(Jwt jwt){


        if(jwt!=null){
            //USER PROFILE
            AppUser user = appUserDetailsService.getAppUserByUsername(jwt.getSubject());

            //Get user id
            Long userId = user.getId();
            //return wishlists
            return wishListRepo.findByUserId(userId).stream().map(item->{


                //Listing
                ListingTransaction currentListing =listingTransactionService.getListingTransaction(item.getListingId(),ListingStatus.PUBLISHED);

                InventoryItem inventoryItem =currentListing.getInventory();
                InventoryItemPrice priceService = inventoryItemPriceService.getPrices(inventoryItem.getId());
                // retrieve product image
                MediaResponse mediaResponse = mediaService.getListingMainImage(inventoryItem.getId());

                return  WishListResponse.builder()
                        .listingId(currentListing.getId())
                        .id(item.getId())
                        .inventoryStatus(currentListing.getInventory().getStatus().name())
                        .sellerPrice(priceService.getStoreNewPrice())
                        .image(mediaResponse.getImage())
                        .build();
            }).toList();
        }
      return List.of(WishListResponse.builder()
              .sellerPrice(BigDecimal.ZERO)
              .image(null)
              .listingId(null)
              .build());
    }


    public Long getWishlistCount(Jwt jwt){
        if(jwt!=null){
            Long buyerId = appUserDetailsService.getAppUserByUsername(jwt.getSubject()).getId();
            return wishListRepo.countByUserId(buyerId);
        }
      return 0L;
    }
}
