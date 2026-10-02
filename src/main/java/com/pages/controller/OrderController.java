package com.pages.controller;

import com.pages.dto.*;
import com.pages.model.OrderReturn;
import com.pages.service.ComplaintService;
import com.pages.service.ListingOrderService;
import com.pages.service.OrderReturnService;
import jakarta.transaction.Transactional;
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
    private final ComplaintService complaintService;
    private final OrderReturnService orderReturnService;

    public OrderController(ListingOrderService listingOrderService, ComplaintService complaintService, OrderReturnService orderReturnService) {
        this.listingOrderService = listingOrderService;
        this.complaintService = complaintService;
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

    @PostMapping("/complaints/new")
    public ResponseDto<Boolean> newComplaints(@AuthenticationPrincipal Jwt jwt, ComplaintRequest dto){
       return complaintService.createComplaint(jwt,dto);
    }

    @GetMapping("/complaints")
    public ListComplaintPage complaints(@AuthenticationPrincipal Jwt jwt,@RequestParam(defaultValue = "1")  Integer page , Integer size){
       return complaintService.allComplaints(jwt,page,size);
    }

    @PreAuthorize("hasAuthority('ROLE_SELLER')")
    @PostMapping("/cancel-order")
    public void cancelOrderBySeller(@AuthenticationPrincipal Jwt jwt,Long orderId){
       orderReturnService.executeSellerCancellation(jwt,orderId);
    }

    @PreAuthorize("hasAuthority('ROLE_USER')")
    @PostMapping(value = "/return-intent",consumes = MediaType.MULTIPART_FORM_DATA_VALUE )
    public ResponseEntity<Boolean> returnIntent(@AuthenticationPrincipal Jwt jwt,@ModelAttribute OrderReturnRequest request){
       return ResponseEntity.ok(orderReturnService.executeReturn(jwt,request));
    }



}
