package com.pages.service;

import com.pages.dto.ListPageShipment;
import com.pages.dto.SellerPayoutResponse;
import com.pages.enums.PayoutStatus;
import com.pages.model.*;
import com.pages.repository.SellerPayoutRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
public class SellerPayoutService {

    private final SellerPayoutRepo sellerPayoutRepo;
    private final InventoryItemPriceService inventoryItemPriceService;
    private final AppUserDetailsService appUserDetailsService;

    public SellerPayoutService(SellerPayoutRepo sellerPayoutRepo, InventoryItemPriceService inventoryItemPriceService, AppUserDetailsService appUserDetailsService) {
        this.sellerPayoutRepo = sellerPayoutRepo;
        this.inventoryItemPriceService = inventoryItemPriceService;
        this.appUserDetailsService = appUserDetailsService;
    }


    @Transactional(readOnly = true)
    public void createPayouts(ListingOrder orderItem) {

        if(!orderItem.getItems().isEmpty()){


            orderItem.getItems()
                    .forEach(order->{

                        InventoryItem inventoryItem =order.getInventoryItem();
                        InventoryItemPrice inventoryItemPrice = inventoryItemPriceService.getPrices(inventoryItem.getId());
                        BigDecimal payoutAmount = inventoryItemPrice.getSellerNewPrice().add(order.getShippingCost());

                        SellerPayout payout = SellerPayout.builder()
                                .listingOrderItem(order)
                                .seller(order.getSeller())
                                .amount(payoutAmount)
                                .currency("PLN")
                                .status(PayoutStatus.CREATED)
                                .build();

                        sellerPayoutRepo.save(payout);
                    });
        }

    }

    @Transactional(readOnly = true)
    private List<SellerPayoutResponse> getMyPayouts(Jwt jwt){
        if(jwt !=null){

            AppUser appUser = appUserDetailsService.getAppUserByUsername(jwt.getSubject());
            Long userId = appUser.getId();

           return sellerPayoutRepo.findBySellerUserId(userId).stream().
                   map(sellerPayout->{
               return SellerPayoutResponse.builder()
                       .seller(sellerPayout.getSeller().getUser().getFirstName()+" "+sellerPayout.getSeller().getUser().getLastName())
                       .amount(sellerPayout.getAmount())
                       .id(sellerPayout.getId())
                       .status(sellerPayout.getStatus())
                       .currency(sellerPayout.getCurrency())
                       .orderNumber(sellerPayout.getListingOrderItem().getListingOrder().getOrderNumber())
                       .listingOrderItem(sellerPayout.getListingOrderItem().getListingId())
                       .build();
           }).toList();
        }
        return List.of();
    }

    @Transactional(readOnly = true)
    private List<SellerPayoutResponse> allPayouts(Jwt jwt){
        if(jwt !=null){

            return sellerPayoutRepo.findAll().stream().
                    map(sellerPayout->{
                        return SellerPayoutResponse.builder()
                                .seller(sellerPayout.getSeller().getUser().getFirstName()+" "+sellerPayout.getSeller().getUser().getLastName())
                                .amount(sellerPayout.getAmount())
                                .id(sellerPayout.getId())
                                .status(sellerPayout.getStatus())
                                .currency(sellerPayout.getCurrency())
                                .orderNumber(sellerPayout.getListingOrderItem().getListingOrder().getOrderNumber())
                                .listingOrderItem(sellerPayout.getListingOrderItem().getListingId())
                                .build();
                    }).toList();
        }
        return List.of();
    }

    @Transactional(readOnly = true)
    public List<SellerPayoutResponse> payouts(Jwt jwt){
        if(jwt!=null){
            AppUser appUser = appUserDetailsService.getAppUserByUsername(jwt.getSubject());
            boolean isAdmin = jwt.getClaimAsStringList("ROLE").contains("ROLE_ADMIN");

            if(isAdmin){
                return allPayouts(jwt);
            }
            return getMyPayouts(jwt);
        }
        return List.of();
    }
}
