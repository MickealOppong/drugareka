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
import com.pages.repository.SellerShipmentRepo;
import com.stripe.exception.EventDataObjectDeserializationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.Transfer;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class SellerPayoutService {

    private final SellerPayoutRepo sellerPayoutRepo;
    private final AppUserDetailsService appUserDetailsService;
    private final SellerProfileRepo sellerProfileRepo;
    private final SellerShipmentRepo sellerShipmentRepo;



    public List<SellerPayoutResponse> allPayouts(Jwt jwt){
        if(jwt==null){
            return null;
        }
       return sellerPayoutRepo.findAll().stream().map(payout->{
          return SellerPayoutResponse.builder()
                   .id(payout.getId())
                   .seller(payout.getSellerNameSnapshot())
                   .status(payout.getStatus())
                   .listingOrderItem(payout.getListingOrderItem().getId())
                   .orderNumber(payout.getListingOrderItem().getListingOrder().getOrderNumber())
                   .currency(payout.getCurrency())
                   .amount(payout.getAmount())
                   .build();
       }).toList();
    }



    public SellerPayout getSellerPayoutById(Long id){
        return sellerPayoutRepo.findById(id).orElse(null);
    }
    public List<SellerPayout> getPayoutsByStatus(PayoutStatus payoutStatus){
        return sellerPayoutRepo.findByStatus(payoutStatus);
    }

    public void save(SellerPayout sellerPayout){
        sellerPayoutRepo.save(sellerPayout);
    }

    @Transactional
    public void createPayouts(ListingOrder order) {

        if(order==null){
            return;
        }

        for(ListingOrderItem item :order.getItems()){

            BigDecimal payoutAmount =item.getSellerPrice();

            //seller name
            String sellerName = item.getSellerNameSnapshot();

            SellerPayout payout = SellerPayout.builder()
                    .listingOrderItem(item)
                    .seller(item.getSeller())
                    .amount(payoutAmount)
                    .sellerNameSnapshot(sellerName)
                    .currency("PLN")
                    .status(PayoutStatus.CREATED)
                    .build();

            sellerPayoutRepo.save(payout);
        }

    }

    @Transactional(readOnly = true)
    private ListPagePayout getMyPayouts(Long userId,int page,int size){
        if(userId !=null){

            Pageable pageable = PageRequest.of(
                    page==0?page:page-1,
                    size,
                    Sort.by(Sort.Direction.DESC, "createdAt")
            );

          Page<SellerPayoutResponse> payouts= sellerPayoutRepo.findBySellerUserId(userId,pageable).
                   map(sellerPayout->{
               return SellerPayoutResponse.builder()
                       .seller(sellerPayout.getSellerNameSnapshot())
                       .amount(sellerPayout.getAmount())
                       .id(sellerPayout.getId())
                       .transferId(sellerPayout.getTransferId())
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
                    .accumulatedItems(getAccumulatedItemsFromPage(payouts.getNumber(),payouts.getSize(),payouts.getTotalElements()))
                    .totalElements(payouts.getTotalElements())
                    .build();
        }
        return ListPagePayout.builder().build();
    }

    @Transactional(readOnly = true)
    public ListPagePayout allPayouts(Jwt jwt,int page,int size){
        if(jwt !=null){

            Pageable pageable = PageRequest.of(
                    page==0?page:page-1,
                    size,
                    Sort.by(Sort.Direction.DESC, "createdAt")
            );


            Page<SellerPayoutResponse> payouts =sellerPayoutRepo.findAll(pageable).
                    map(sellerPayout->{
                        return SellerPayoutResponse.builder()
                                .seller(sellerPayout.getSellerNameSnapshot())
                                .amount(sellerPayout.getAmount())
                                .id(sellerPayout.getId())
                                .status(sellerPayout.getStatus())
                                .currency(sellerPayout.getCurrency())
                                .transferId(sellerPayout.getTransferId())
                                .orderNumber(sellerPayout.getListingOrderItem().getListingOrder().getOrderNumber())
                                .listingOrderItem(sellerPayout.getListingOrderItem().getListingId())
                                .build();
                    });
            return ListPagePayout.builder()
                    .payouts(payouts.getContent())
                    .totalPages(payouts.getTotalPages())
                    .page(payouts.getNumber())
                    .accumulatedItems(getAccumulatedItemsFromPage(payouts.getNumber(),payouts.getSize(),payouts.getTotalElements()))
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
            return getMyPayouts(appUser.getId(),page,size);
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

           ShipmentStatus shipmentStatus =sellerShipmentRepo.findByListingOrderItemId(payout.getListingOrderItem().getId())
                   .orElseThrow(()->new IllegalStateException("Item must be delivered befoe payout")).getShipmentStatus();

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


    @Transactional
    public ResponseDto<Object> updateStatus(Jwt jwt,Long payoutId,String status){
        if(jwt==null){
            return ResponseDto.<Object>builder()
                    .httpStatus(HttpStatus.FORBIDDEN.value())
                    .message("Not authorised")
                    .build();
        }
        SellerPayout payout= sellerPayoutRepo.findById(payoutId).orElse(null);


        if(payout!=null){


            payout.setStatus(getStatusFromString(status));

            sellerPayoutRepo.save(payout);

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

    public PayoutStatus getStatusFromString(String status){
        return switch (status.toLowerCase()){
            case "paid" ->PayoutStatus.PAID;
            case"created"->PayoutStatus.CREATED;
            case"processing"->PayoutStatus.PROCESSING;
            case"transferred"->PayoutStatus.TRANSFERRED;
            case "approved"->PayoutStatus.APPROVED;
            case "reversed"->PayoutStatus.REVERSED;
            case"refund"->PayoutStatus.REFUND;
            case"pending"->PayoutStatus.PENDING;
            case"failed"->PayoutStatus.FAILED;
            case "completed"->PayoutStatus.COMPLETED;
            default -> throw new IllegalStateException("Unexpected value: " + status.toLowerCase());
        };
    }

    public void createTotalRefund(ListingOrderItem orderItem) {

        if(orderItem!=null){

            BigDecimal refundAmount =orderItem.getSellerPrice();

            SellerPayout payout = SellerPayout.builder()
                    .listingOrderItem(orderItem)
                    .seller(orderItem.getSeller())
                    .sellerNameSnapshot(orderItem.getSellerNameSnapshot())
                    .amount(refundAmount.negate())
                    .currency("PLN")
                    .status(PayoutStatus.REFUND)
                    .build();

            sellerPayoutRepo.save(payout);
        }

    }

    public int getAccumulatedItemsFromPage(int currentPage,int pageSize,long totalElements){
        // 1. Calculate how many items exist up to the start boundary of the current page
        int itemsBeforeCurrentPage = currentPage * pageSize;

        // 2. Compute the exact remaining items left in the database for this page and beyond
        int remainingItems = (int)totalElements - itemsBeforeCurrentPage;

        // 3.  If remaining items are less than page size,
        // use the exact remainder; otherwise, add a full standard page size block.
        int accumulatedTotal;
        if (remainingItems < pageSize) {
            accumulatedTotal = itemsBeforeCurrentPage + remainingItems; // Caps exactly at TotalElements
        } else {
            accumulatedTotal = itemsBeforeCurrentPage + pageSize;       // Adds a standard full page block
        }
        return accumulatedTotal;
    }

    public void createRefund(ListingOrderItem orderItem) {

        if(orderItem!=null){


            BigDecimal refundAmount =orderItem.getSellerPrice();

            SellerPayout payout = SellerPayout.builder()
                    .listingOrderItem(orderItem)
                    .seller(orderItem.getSeller())
                    .sellerNameSnapshot(orderItem.getSellerNameSnapshot())
                    .amount(refundAmount.negate())
                    .currency("PLN")
                    .status(PayoutStatus.REFUND)
                    .build();

            sellerPayoutRepo.save(payout);
        }

    }

    public void handleTransferCreated(Event event) {
        Transfer transfer = null;
        try {
            transfer = (Transfer) event
                    .getDataObjectDeserializer()
                    .deserializeUnsafe();
        } catch (EventDataObjectDeserializationException e) {
            throw new RuntimeException(e);
        }

        if (transfer == null) {
            throw new IllegalStateException(
                    "Could not deserialize transfer for event "
                            + event.getId()
            );
        }

        String transferId = transfer.getId();

        SellerPayout sellerPayout = sellerPayoutRepo
                .findByTransferId(transferId)
                .orElseThrow(() -> new IllegalStateException(
                        "No associated SellerPayout for transfer: "
                                + transferId
                ));

        // A reversal is a separate lifecycle event and must not be overwritten.
        if (sellerPayout.getStatus() == PayoutStatus.REVERSED
                || sellerPayout.getStatus() == PayoutStatus.PAID) {
            return;
        }

        if (sellerPayout.getStatus() == PayoutStatus.TRANSFERRED) {
            return;
        }

        sellerPayout.setStatus(PayoutStatus.TRANSFERRED);
        sellerPayoutRepo.save(sellerPayout);
    }


    public void handleTransferReversed(Event event){
        Transfer transfer = null;
        try {
            transfer = (Transfer) event
                    .getDataObjectDeserializer()
                    .deserializeUnsafe();
        } catch (EventDataObjectDeserializationException e) {
            throw new RuntimeException(e);
        }

        if (transfer == null) {
            throw new IllegalStateException(
                    "Could not deserialize transfer for event "
                            + event.getId()
            );
        }

        String transferId = transfer.getId();

        SellerPayout sellerPayout = sellerPayoutRepo
                .findByTransferId(transferId)
                .orElseThrow(() -> new IllegalStateException(
                        "No associated SellerPayout for transfer: "
                                + transferId
                ));

        // Ignore duplicate reversal events.
        if (sellerPayout.getStatus() == PayoutStatus.REVERSED) {
            return;
        }

        sellerPayout.setStatus(PayoutStatus.REVERSED);
        sellerPayoutRepo.save(sellerPayout);
    }
}
