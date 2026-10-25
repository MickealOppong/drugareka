package com.pages.controller;

import com.pages.dto.*;
import com.pages.service.OrderDisputeService;
import com.pages.service.ListingOrderService;
import com.pages.service.OrderReturnService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final ListingOrderService listingOrderService;
    private final OrderReturnService orderReturnService;

    public OrderController(ListingOrderService listingOrderService, OrderReturnService orderReturnService) {
        this.listingOrderService = listingOrderService;
        this.orderReturnService = orderReturnService;
    }

    @GetMapping("/selling")
    public OrderPageDto selling(@AuthenticationPrincipal Jwt jwt,@RequestParam(defaultValue = "1")  Integer page , Integer size){
        return listingOrderService.mySales(jwt,page,size);
    }

    @GetMapping("/buying/details")
    public List<OrderDto> buyingDetails(@AuthenticationPrincipal Jwt jwt, @RequestParam Long id){
        return listingOrderService.myBuyingDetails(jwt, id);
    }
    @GetMapping("/buying")
    public OrderPageDto buying(@AuthenticationPrincipal Jwt jwt,@RequestParam(defaultValue = "1")  Integer page , Integer size){
        return listingOrderService.myPurchases(jwt,page,size);
    }


    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @GetMapping("/orders")
    public OrderPageDto getPurchases(@AuthenticationPrincipal Jwt jwt,@RequestParam(defaultValue = "1")  Integer page , Integer size){
        return listingOrderService.storeOrders(jwt,page,size);
    }



    //@PreAuthorize("hasAuthority('ROLE_SELLER')")
    @PostMapping("/cancel-seller-order")
    public void cancelOrderBySeller(@AuthenticationPrincipal Jwt jwt,Long orderItemId){
       orderReturnService.executeSellerCancellation(jwt,orderItemId);
    }



    @PostMapping("/cancel-buyer-order")
    public void cancelOrderByBuyer(@AuthenticationPrincipal Jwt jwt,Long orderItemId){
        orderReturnService.executeBuyerCancellation(jwt,orderItemId);
    }






}
