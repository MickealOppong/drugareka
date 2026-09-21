package com.pages.service;

import com.pages.dto.*;
import com.pages.enums.ListingStatus;
import com.pages.enums.ShipmentStatus;
import com.pages.exception.InvalidOperationException;
import com.pages.model.*;
import com.pages.repository.SellerShipmentRepo;
import com.pages.util.UtilService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;


@Slf4j
@Service
public class ShipmentService {

    private final SellerShipmentRepo sellerShipmentRepo;
    private final AppUserDetailsService appUserDetailsService;
    private final SellerProfileService sellerProfileService;
    private final EmailNotificationService emailNotificationService;
    private final ListingOrderService listingOrderService;

    public ShipmentService(SellerShipmentRepo sellerShipmentRepo, AppUserDetailsService appUserDetailsService, SellerProfileService sellerProfileService, EmailNotificationService emailNotificationService, ListingOrderService listingOrderService) {
        this.sellerShipmentRepo = sellerShipmentRepo;
        this.appUserDetailsService = appUserDetailsService;
        this.sellerProfileService = sellerProfileService;
        this.emailNotificationService = emailNotificationService;
        this.listingOrderService = listingOrderService;
    }

    public SellerShipment findOrCreateShipment(ListingOrderItem listingOrderItem){
        return   sellerShipmentRepo.findByListingOrderItemId(listingOrderItem.getId())
                .orElseGet(()->{
                   SellerShipment shipment= SellerShipment.builder()
                            .listingOrderItem(listingOrderItem)
                            .seller(listingOrderItem.getSeller())
                            .deliveredAt(null)
                           .shippingAddress(listingOrderItem.getListingOrder().getShippingAddress())
                           .shipmentStatus(ShipmentStatus.CREATED)
                            .shippedAt(null)
                            .build();
                   return sellerShipmentRepo.save(shipment);
                });
    }

    public void createShipment(List<ListingOrderItem> listingOrderItem){


        listingOrderItem.forEach(item->{
            SellerShipment shipment= SellerShipment.builder()
                   .listingOrderItem(item)
                   .seller(item.getSeller())
                    .deliveredAt(null)
                    .shippingAddress(item.getListingOrder().getShippingAddress())
                    .shipmentStatus(ShipmentStatus.AWAITING_SHIPMENT)
                    .shippedAt(null)
                    .build();sellerShipmentRepo.save(shipment);

        });

    }

    @Transactional(readOnly = true)
    private ListPageShipment sellerShipments(Jwt jwt,Integer page,Integer size){
        if(jwt !=null) {
            AppUser appUser = appUserDetailsService.getAppUserByUsername(jwt.getSubject());

            SellerProfile seller = sellerProfileService.getSellerProfile(appUser.getId());

            Pageable pageable = PageRequest.of(
                    page==0?page:page-1,
                    size,
                    Sort.by(Sort.Direction.DESC, "createdAt")
            );

           Page<ShipmentResponse>  shipments = sellerShipmentRepo.findBySeller(seller,pageable)
                    .map(shipment -> {
                        return ShipmentResponse.builder()
                                .id(shipment.getId())
                                .orderNumber(shipment.getListingOrderItem().getListingOrder().getOrderNumber())
                                .deliveredAt(shipment.getDeliveredAt())
                                .deliveryAddress(shipment.getShippingAddress())
                                .listingOrderId(shipment.getListingOrderItem().getListingId())
                                .seller(shipment.getSeller().getUser().getFirstName()+" "+shipment.getSeller().getUser().getLastName())
                                .shippedAt(shipment.getShippedAt())
                                .trackingNumber(shipment.getTrackingNumber())
                                .status(shipment.getShipmentStatus())
                                .build();
                    });

            return ListPageShipment.builder()
                    .shipments(shipments.getContent())
                    .totalPages(shipments.getTotalPages())
                    .page(shipments.getNumber())
                    .totalElements(shipments.getTotalElements())
                    .pageSize(shipments.getSize())
                    .build();
        }
        return ListPageShipment.builder().build();
    }

    public ShipmentStatus shipmentStatus(ListingOrderItem listingOrderItem){
        return sellerShipmentRepo.findByListingOrderItemId(listingOrderItem.getId()).map(SellerShipment::getShipmentStatus).orElse(null);
    }

    public SellerShipment shipment(ListingOrderItem listingOrderItem){
        return sellerShipmentRepo.findByListingOrderItemId(listingOrderItem.getId()).orElse(null);
    }

    private ShipmentStatus getShippingStatus(String status){
        String cleanStatus = status.trim().toUpperCase();
        return switch (cleanStatus) {
            case "AWAITING_SHIPMENT" -> ShipmentStatus.AWAITING_SHIPMENT;
            case "SHIPPED" -> ShipmentStatus.SHIPPED;
            case "DELIVERED" -> ShipmentStatus.DELIVERED;
            case "RETURNED" -> ShipmentStatus.RETURNED;
            default -> ShipmentStatus.CREATED;
        };

    }

    @Transactional
    public ResponseDto<Boolean> updateShippingStatus(Jwt jwt, ShipmentRequest request) {
        if (jwt != null) {
         SellerShipment shipment=   sellerShipmentRepo.findById(request.getShipmentId()).orElse(null);
         if(shipment!=null){
             // 1. EXTRACT FRONTEND REQUEST DATE TIMESTAMP SAFELY
             Instant requestedActionDate = request.getCreatedAt().atStartOfDay(ZoneId.systemDefault()).toInstant();

             // If user is updating to SHIPPED or DELIVERED, they MUST supply a date parameter!
             if ((request.getStatus().equals("SHIPPED") || request.getStatus().equals("DELIVERED"))
                     && requestedActionDate == null) {
               throw new InvalidOperationException("Brak wskazanej daty operacji logistycznej.");
             }

             // 2. RUN COMPREHENSIVE BUSINESS RULE LIFECYCLE CHECKS
             if (request.getStatus().equals("SHIPPED")) {
                 // Safe: Setting Shipped data can happen directly
                 shipment.setShippedAt(requestedActionDate);
             }

             if (request.getStatus().equals("DELIVERED")) {
                 // Gather pre-existing shipped timestamp from database row records
                 Instant existingShippedAt = shipment.getShippedAt();

                 // Prevent delivery modifications if item was never shipped before
                 if (existingShippedAt == null) {

                     throw new InvalidOperationException("Nie można oznaczyć jako doręczone przed nadaniem przesyłki.");
                 }

                 //  Prevent delivery date from sitting chronologically BEFORE the shipment date
                 if (requestedActionDate.isBefore(existingShippedAt)) {
                     throw new InvalidOperationException("Data doręczenia nie może być wcześniejsza niż data nadania przesyłki.");
                 }

                 shipment.setDeliveredAt(requestedActionDate);
             }

             if (request.getStatus().equals("RETURNED")) {
                 shipment.setDeliveredAt(requestedActionDate != null ? requestedActionDate : Instant.now());
             }

             // 3. PERSIST CLEAN METADATA STATES
             shipment.setShipmentStatus(getShippingStatus(request.getStatus()));
             shipment.setComment(request.getComment());
             shipment.setTrackingNumber(request.getTrackingNumber());


             SellerShipment sellerShipment = sellerShipmentRepo.save(shipment);
             ListingOrderItem listingOrderItem  = sellerShipment.getListingOrderItem();
             String token = "";
             if (request.getStatus().equalsIgnoreCase(ShipmentStatus.SHIPPED.name()) || request.getStatus().equalsIgnoreCase(ShipmentStatus.DELIVERED.name())) {

                  token = UtilService.generateReceiptConfirmationToken();

                 listingOrderItem.setReceiptConfirmationToken(token);

                 // Token valid for 14 days
                 listingOrderItem.setReceiptConfirmationTokenExpiresAt(
                        Instant.now().plus(3, ChronoUnit.DAYS)
                 );

                 listingOrderItem.setReceiptConfirmedAt(null);

                 listingOrderService.save(listingOrderItem);
             }

             ListingOrder listingOrder = listingOrderItem.getListingOrder();

             AppUser buyer = appUserDetailsService.getAppUserId(listingOrder.getBuyerId());

             String buyerName = buyer.getFirstName()+" "+buyer.getLastName();
             String product = listingOrderItem.getInventoryItem().getProductCatalog().getName();

             String shippingMethod = listingOrderItem.getInventoryItem().getShippingMethod().name();


             emailNotificationService.sendShipmentStatusToBuyer(buyer.getUsername(),buyerName,listingOrder.getOrderNumber()
                     ,product,sellerShipment.getShipmentStatus().name(),sellerShipment.getTrackingNumber(),shippingMethod,token);
         }

            return ResponseDto.<Boolean>builder()
                    .data(true)
                    .message("Shipment successfully updated.")
                    .httpStatus(HttpStatus.OK.value())
                    .build();
        }

        return ResponseDto.<Boolean>builder()
                .data(false)
                .message("Error fetching authorization metadata data.")
                .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .build();
    }



    @Transactional(readOnly = true)
    private ListPageShipment allShipments(Jwt jwt,Integer page,Integer size){
        if(jwt !=null) {

            Pageable pageable = PageRequest.of(
                    page==0?page:page-1,
                    size,
                    Sort.by(Sort.Direction.DESC, "createdAt")
            );

            Page<ShipmentResponse>  shipments = sellerShipmentRepo.findAll(pageable)
                    .map(shipment -> {
                        return ShipmentResponse.builder()
                                .id(shipment.getId())
                                .deliveredAt(shipment.getDeliveredAt())
                                .deliveryAddress(shipment.getShippingAddress())
                                .listingOrderId(shipment.getListingOrderItem().getListingId())
                                .seller(shipment.getSeller().getUser().getFirstName()+" "+shipment.getSeller().getUser().getLastName())
                                .shippedAt(shipment.getShippedAt())
                                .trackingNumber(shipment.getTrackingNumber())
                                .status(shipment.getShipmentStatus())
                                .orderNumber(shipment.getListingOrderItem().getListingOrder().getOrderNumber())
                                .build();
                    });

            return ListPageShipment.builder()
                    .shipments(shipments.getContent())
                    .totalPages(shipments.getTotalPages())
                    .page(shipments.getNumber())
                    .totalElements(shipments.getTotalElements())
                    .pageSize(shipments.getSize())
                    .build();
        }
        return ListPageShipment.builder().build();
    }

    @Transactional(readOnly = true)
    public ListPageShipment shipments(Jwt jwt,Integer page,Integer size){
        if(jwt!=null){

            boolean isAdmin = jwt.getClaimAsStringList("ROLE").contains("ROLE_ADMIN");

            if(isAdmin){
                return allShipments(jwt,page,size);
            }
            return sellerShipments(jwt,page,size);
        }
        return ListPageShipment.builder().build();
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public Long myShipments(Jwt jwt){
        if(jwt!=null){
            AppUser appUser = appUserDetailsService.getAppUserByUsername(jwt.getSubject());
           SellerProfile seller= sellerProfileService.getSellerProfile(appUser.getId());

           return sellerShipmentRepo.findBySeller(seller).stream().map(SellerShipment::getShipmentStatus)
                   .filter(shipmentStatus -> shipmentStatus.name().equals(ShipmentStatus.AWAITING_SHIPMENT.name()))
                   .count();
        }
        return 0L;
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public SellerShipment recentShipment(Jwt jwt){
        if(jwt!=null){
            AppUser appUser = appUserDetailsService.getAppUserByUsername(jwt.getSubject());
            SellerProfile seller= sellerProfileService.getSellerProfile(appUser.getId());

            return sellerShipmentRepo.findFirstBySellerIdOrderByCreatedAtDesc(seller.getId()).orElse(null);
        }
        return null;
    }


}

