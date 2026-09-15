package com.pages.service;

import com.pages.dto.ListPageShipment;
import com.pages.dto.ShipmentResponse;
import com.pages.enums.ShipmentStatus;
import com.pages.model.AppUser;
import com.pages.model.ListingOrderItem;
import com.pages.model.SellerProfile;
import com.pages.model.SellerShipment;
import com.pages.repository.SellerShipmentRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;


@Slf4j
@Service
public class ShipmentService {

    private final SellerShipmentRepo sellerShipmentRepo;
    private final ListingOrderService listingOrderService;
    private final AppUserDetailsService appUserDetailsService;
    private final SellerProfileService sellerProfileService;

    public ShipmentService(SellerShipmentRepo sellerShipmentRepo, ListingOrderService listingOrderService, AppUserDetailsService appUserDetailsService, SellerProfileService sellerProfileService) {
        this.sellerShipmentRepo = sellerShipmentRepo;
        this.listingOrderService = listingOrderService;
        this.appUserDetailsService = appUserDetailsService;
        this.sellerProfileService = sellerProfileService;
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
                    .shipmentStatus(ShipmentStatus.CREATED)
                    .shippedAt(null)
                    .build();
             sellerShipmentRepo.save(shipment);
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

           Page<ShipmentResponse>  shipments = sellerShipmentRepo.findBySellerAndShipmentStatus(seller, ShipmentStatus.CREATED,pageable)
                    .map(shipment -> {
                        return ShipmentResponse.builder()
                                .id(shipment.getId())
                                .deliveredAt(shipment.getDeliveredAt())
                                .deliveryAddress(shipment.getShippingAddress())
                                .listingOrderId(shipment.getListingOrderItem().getListingId())
                                .seller(shipment.getSeller().getUser().getFirstName()+" "+shipment.getSeller().getUser().getLastName())
                                .shippedAt(shipment.getShippedAt())
                                .status(shipment.getShipmentStatus())
                                .build();
                    });

            return ListPageShipment.builder()
                    .shipments(shipments.getContent())
                    .totalPages(shipments.getTotalPages())
                    .page(shipments.getNumber())
                    .totalElements(shipments.getTotalElements())
                    .build();
        }
        return ListPageShipment.builder().build();
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
                                .status(shipment.getShipmentStatus())
                                .build();
                    });

            return ListPageShipment.builder()
                    .shipments(shipments.getContent())
                    .totalPages(shipments.getTotalPages())
                    .page(shipments.getNumber())
                    .totalElements(shipments.getTotalElements())
                    .build();
        }
        return ListPageShipment.builder().build();
    }

    @Transactional(readOnly = true)
    public ListPageShipment shipments(Jwt jwt,Integer page,Integer size){
        if(jwt!=null){
            AppUser appUser = appUserDetailsService.getAppUserByUsername(jwt.getSubject());
            boolean isAdmin = jwt.getClaimAsStringList("ROLE").contains("ROLE_ADMIN");

            if(isAdmin){
                return allShipments(jwt,page,size);
            }
            return sellerShipments(jwt,page,size);
        }
        return ListPageShipment.builder().build();
    }
}

