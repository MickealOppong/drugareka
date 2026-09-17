package com.pages.service;

import com.pages.dto.AnalyticsDto;
import com.pages.dto.SellingActivityResponse;
import com.pages.enums.InventoryStatus;
import com.pages.enums.OrderStatus;
import com.pages.enums.ShipmentStatus;
import com.pages.model.*;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import javax.imageio.plugins.jpeg.JPEGImageReadParam;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class AnalyticsService {

    @Autowired
    private ListingTransactionService listingTransactionService;
    @Autowired
    private AppUserDetailsService appUserDetailsService;

    @Autowired
    private WishListService wishListService;
    @Autowired
    private ShipmentService shipmentService;
    @Autowired
    private SellerProfileService sellerProfileService;

    @Autowired
    private ListingOrderService listingOrderService;


    private Long getSellerListingCount(Jwt jwt){

        if(jwt ==null){
            return 0L;
        }
        String username = jwt.getSubject();
        AppUser currentUser = appUserDetailsService.getAppUserByUsername(username);
        SellerProfile sellerProfile = sellerProfileService.getSellerProfile(currentUser.getId());

        return listingTransactionService.getSellerPublishedListingCount(sellerProfile.getId());
    }


    private Long  totalStoreOrders(Jwt jwt){

      return listingOrderService.totalOrders(jwt);
    }


    private Long getWishlistCount(Jwt jwt){

        return wishListService.getWishlistCount(jwt);
    }



    private Long itemSold(Jwt jwt){
       return listingOrderService.mySalesCount(jwt);
    }
    private Long itemSoldCancelled(Jwt jwt){
        return listingOrderService.mySalesCancelledCount(jwt);
    }

    private Long orders(Jwt jwt){
        return listingOrderService.myPurchaseCount(jwt);
    }


    private Long awaitingShipmentCount(Jwt jwt){
      return   shipmentService.myShipments(jwt);
    }

    public AnalyticsDto dashboardAnalytics(Jwt jwt){

        BigDecimal payout = sellerProfileService.outStandingPayout(jwt);

        return AnalyticsDto.builder()
                .itemSoldCount(itemSold(jwt))
                .ordersCount(orders(jwt))
                .listingCount(getSellerListingCount(jwt))
                .itemSoldCancelledCount(itemSoldCancelled(jwt))
                .wishlistCount(getWishlistCount(jwt))
                .outstandingPayout(payout)
                .awaitingShipmentCount(awaitingShipmentCount(jwt))
                .build();
    }

    private ListingOrderItem getLastestSaleActivity(Jwt jwt){
        if(jwt ==null){
            return null;
        }
        String username = jwt.getSubject();
        AppUser currentUser = appUserDetailsService.getAppUserByUsername(username);
        SellerProfile sellerProfile = sellerProfileService.getSellerProfile(currentUser.getId());
       return listingOrderService.getRecentSaleBySeller(sellerProfile.getId());
    }


    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<SellingActivityResponse> recentSellingActivities(Jwt jwt){
        List<SellingActivityResponse> dto = new ArrayList<>();

        //recent sale
        ListingOrderItem orderItem = getLastestSaleActivity(jwt);

        if(orderItem!=null){
            SellingActivityResponse sales= SellingActivityResponse.builder()
                    .id(orderItem.getId())
                    .name(orderItem.getInventoryItem().getProductCatalog().getName())
                    .status(orderItem.getInventoryItem().getStatus().name())
                    .type("sold")
                    .path("/account/orders")
                    .build();
            dto.add(sales);
        }
        //recent shipment
        SellerShipment recentShipment = shipmentService.recentShipment(jwt);
        if(recentShipment!=null){
            SellingActivityResponse shipment= SellingActivityResponse.builder()
                    .id(recentShipment.getId())
                    .name(recentShipment.getListingOrderItem().getInventoryItem().getProductCatalog().getName())
                    .status(recentShipment.getShipmentStatus().name())
                    .type("shipment")
                    .path("/account/shipments")
                    .build();
            dto.add(shipment);
        }

        //recent listing
        ListingTransaction recentListing= listingTransactionService.recentListing(jwt);
        if(recentListing !=null){
            SellingActivityResponse recentItem= SellingActivityResponse.builder()
                    .id(recentListing.getId())
                    .name(recentListing.getInventory().getProductCatalog().getName())
                    .status(recentListing.getListingStatus().name())
                    .type("active")
                    .path("/account/listings/me")
                    .build();
            dto.add(recentItem);
        }
        return dto;

    }
}
