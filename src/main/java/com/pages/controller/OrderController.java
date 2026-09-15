package com.pages.controller;

import com.pages.dto.*;
import com.pages.service.ComplaintService;
import com.pages.service.ListingOrderService;
import lombok.extern.slf4j.Slf4j;
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
    private final ComplaintService complaintService;

    public OrderController(ListingOrderService listingOrderService, ComplaintService complaintService) {
        this.listingOrderService = listingOrderService;
        this.complaintService = complaintService;
    }

    @GetMapping("/buying")
    public OrderPageDto buying(@AuthenticationPrincipal Jwt jwt,@RequestParam(defaultValue = "1")  Integer page , Integer size){
        return listingOrderService.mySales(jwt,page,size);
    }

    @GetMapping("/selling")
    public OrderPageDto selling(@AuthenticationPrincipal Jwt jwt,@RequestParam(defaultValue = "1")  Integer page , Integer size){
        return listingOrderService.myPurchases(jwt,page,size);
    }


    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @GetMapping("/orders")
    public OrderPageDto getPurchases(@AuthenticationPrincipal Jwt jwt,@RequestParam(defaultValue = "1")  Integer page , Integer size){
        return listingOrderService.storeOrders(jwt,page,size);
    }

    @PostMapping("/complaints/new")
    public ResponseDto<Boolean> newComplaints(@AuthenticationPrincipal Jwt jwt, ComplaintRequest dto){
        log.info("{}",dto);
       return complaintService.createComplaint(jwt,dto);
    }

    @GetMapping("/complaints")
    public ListComplaintPage complaints(@AuthenticationPrincipal Jwt jwt,@RequestParam(defaultValue = "1")  Integer page , Integer size){
       return complaintService.allComplaints(jwt,page,size);
    }
}
