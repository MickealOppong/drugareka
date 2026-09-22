package com.pages.service;

import com.pages.dto.ListPagePayout;
import com.pages.dto.ListPageShipment;
import com.pages.dto.ResponseDto;
import com.pages.dto.SellerPayoutResponse;
import com.pages.enums.PayoutStatus;
import com.pages.enums.ShipmentStatus;
import com.pages.model.*;
import com.pages.repository.SellerPayoutRepo;
import com.pages.repository.SellerProfileRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;

import static org.antlr.v4.runtime.tree.xpath.XPath.findAll;

@Slf4j
@Service
public class SellerPayoutService {

    private final SellerPayoutRepo sellerPayoutRepo;
    private final InventoryItemPriceService inventoryItemPriceService;
    private final AppUserDetailsService appUserDetailsService;
    private final ShipmentService shipmentService;
    private final SellerProfileRepo sellerProfileRepo;

    public SellerPayoutService(SellerPayoutRepo sellerPayoutRepo, InventoryItemPriceService inventoryItemPriceService, AppUserDetailsService appUserDetailsService, ShipmentService shipmentService, SellerProfileRepo sellerProfileRepo) {
        this.sellerPayoutRepo = sellerPayoutRepo;
        this.inventoryItemPriceService = inventoryItemPriceService;
        this.appUserDetailsService = appUserDetailsService;
        this.shipmentService = shipmentService;
        this.sellerProfileRepo = sellerProfileRepo;
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

    @Transactional
    public ResponseDto<Object> settleAmountDue(Jwt jwt,Long payoutId,String paidAt){
        if(jwt==null){
            return ResponseDto.<Object>builder()
                    .httpStatus(HttpStatus.FORBIDDEN.value())
                    .message("Not authorised")
                    .build();
        }
       SellerPayout payout= sellerPayoutRepo.findById(payoutId).orElse(null);


       if(payout!=null){

           ShipmentStatus shipmentStatus =shipmentService.getShipmentStatus(payout.getListingOrderItem().getListingOrder().getOrderNumber());

           if(!shipmentStatus.equals(ShipmentStatus.DELIVERED)){
               return ResponseDto.<Object>builder()
                       .httpStatus(HttpStatus.FORBIDDEN.value())
                       .message("Settlement can only be initiated for completed order")
                       .build();
           }

           payout.setStatus(PayoutStatus.PAID);
          LocalDate inputDate = LocalDate.parse(paidAt);
          Instant settlementDate =inputDate.atStartOfDay(ZoneId.systemDefault()).toInstant();

          payout.setPaidAt(settlementDate);
           SellerPayout updatedPayout = sellerPayoutRepo.save(payout);

          SellerProfile sellerProfile= sellerProfileRepo.findByUserId(payout.getSeller().getUser().getId()).orElse(null);

          if(sellerProfile!=null){
              BigDecimal outstanding = sellerProfile.getTotalSettlement()!=null?sellerProfile.getTotalSettlement():BigDecimal.ZERO;
              sellerProfile.setTotalSettlement(outstanding.add(updatedPayout.getAmount()));
              sellerProfileRepo.save(sellerProfile);
          }

           return ResponseDto.<Object>builder()
                   .httpStatus(HttpStatus.OK.value())
                   .data(true)
                   .message("Payment completed")
                   .build();
       }
        return ResponseDto.<Object>builder()
                .httpStatus(HttpStatus.BAD_REQUEST.value())
                .data(false)
                .message("Record does not exist")
                .build();
    }
}
