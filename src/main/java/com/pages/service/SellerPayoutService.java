package com.pages.service;

import com.pages.dto.ListPagePayout;
import com.pages.dto.ListPageShipment;
import com.pages.dto.SellerPayoutResponse;
import com.pages.enums.PayoutStatus;
import com.pages.model.*;
import com.pages.repository.SellerPayoutRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.antlr.v4.runtime.tree.xpath.XPath.findAll;

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
    private ListPagePayout getMyPayouts(Jwt jwt,int page,int size){
        if(jwt !=null){

            AppUser appUser = appUserDetailsService.getAppUserByUsername(jwt.getSubject());
            Long userId = appUser.getId();

            Pageable pageable = PageRequest.of(
                    page==0?page:page-1,
                    size,
                    Sort.by(Sort.Direction.DESC, "createdAt")
            );

          Page<SellerPayoutResponse> payouts= sellerPayoutRepo.findBySellerUserId(userId,pageable).
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
           });
            return ListPagePayout.builder()
                    .payouts(payouts.getContent())
                    .totalPages(payouts.getTotalPages())
                    .page(payouts.getNumber())
                    .pageSize(payouts.getSize())
                    .totalElements(payouts.getTotalElements())
                    .build();
        }
        return ListPagePayout.builder().build();
    }

    @Transactional(readOnly = true)
    private ListPagePayout allPayouts(Jwt jwt,int page,int size){
        if(jwt !=null){

            Pageable pageable = PageRequest.of(
                    page==0?page:page-1,
                    size,
                    Sort.by(Sort.Direction.DESC, "createdAt")
            );


            Page<SellerPayoutResponse> payouts =sellerPayoutRepo.findAll(pageable).
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
                    });
            return ListPagePayout.builder()
                    .payouts(payouts.getContent())
                    .totalPages(payouts.getTotalPages())
                    .page(payouts.getNumber())
                    .pageSize(payouts.getSize())
                    .totalElements(payouts.getTotalElements())
                    .build();
        }
        return ListPagePayout.builder().build();
    }

    @Transactional(readOnly = true)
    public ListPagePayout payouts(Jwt jwt,int page,int size){
        if(jwt!=null){
            AppUser appUser = appUserDetailsService.getAppUserByUsername(jwt.getSubject());
            boolean isAdmin = jwt.getClaimAsStringList("ROLE").contains("ROLE_ADMIN");

            if(isAdmin){
                return allPayouts(jwt,page,size);
            }
            return getMyPayouts(jwt,page,size);
        }
        return ListPagePayout.builder().build();
    }
}
