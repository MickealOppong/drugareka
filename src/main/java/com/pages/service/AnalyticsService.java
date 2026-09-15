package com.pages.service;

import com.pages.dto.AnalyticsDto;
import com.pages.model.AppUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class AnalyticsService {

    @Autowired
    private ListingTransactionService listingTransactionService;
    @Autowired
    private AppUserDetailsService appUserDetailsService;

    @Autowired
    private WishListService wishListService;

    @Autowired
    private ListingOrderService listingOrderService;

    private Long getSellerListingCount(Jwt jwt){

        if(jwt ==null){
            return 0L;
        }
        String username = jwt.getSubject();
        AppUser currentUser = appUserDetailsService.getAppUserByUsername(username);
        Long currentUserId = currentUser.getId();

        return listingTransactionService.getSellerPublishedListingCount(currentUserId);
    }


    private Long  totalStoreOrders(Jwt jwt){

      return listingOrderService.totalOrders(jwt);
    }


    private Long getWishlistCount(Jwt jwt){

        return wishListService.getWishlistCount(jwt);
    }


    public AnalyticsDto dashboardAnalytics(Jwt jwt){
        return AnalyticsDto.builder()
                .awaitingShipmentCount(0L)
                .itemSoldCount(0L)
                .itemSoldCount(0L)
                .ordersCount(0L)
                .listingCount(getSellerListingCount(jwt))
                .wishlistCount(getWishlistCount(jwt))
                .outstandingPayout(BigDecimal.valueOf(0.00))
                .build();
    }

}
