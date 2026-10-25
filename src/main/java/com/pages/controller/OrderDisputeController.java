package com.pages.controller;

import com.pages.dto.DisputeResponse;
import com.pages.dto.ListComplaintPage;
import com.pages.dto.OrderReturnRequest;
import com.pages.dto.ResponseDto;
import com.pages.service.OrderDisputeService;
import com.pages.service.OrderReturnService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dispute")
@RequiredArgsConstructor
public class OrderDisputeController {

    private final OrderDisputeService orderDisputeService;
    private final OrderReturnService orderReturnService;


    @PostMapping(value = "/return-intent",consumes = MediaType.MULTIPART_FORM_DATA_VALUE )
    public ResponseEntity<Boolean> returnIntent(@AuthenticationPrincipal Jwt jwt, @ModelAttribute OrderReturnRequest request){
        return ResponseEntity.ok(orderDisputeService.logDispute(jwt,request));
    }

    @GetMapping("/complaints")
    public ListComplaintPage orderComplaints(@AuthenticationPrincipal Jwt jwt, int page, int size){
       return  orderDisputeService.allComplaints(jwt,page,size);
    }

    @GetMapping("/complaint")
    public DisputeResponse buyerComplaint(@AuthenticationPrincipal Jwt jwt, String orderNumber){
        return  orderDisputeService.getOrderDispute(jwt,orderNumber);
    }

    @PutMapping("/update")
    public ResponseDto<Object> updateDisputeWithSellerInformation(@AuthenticationPrincipal Jwt jwt, String orderNumber, String decision, String reason){
       return orderDisputeService.executeSellerUpdate(jwt,orderNumber,decision,reason);
    }

    @PutMapping("/admin-review")
    public ResponseDto<Object> updateDisputeWithAdminDecision(@AuthenticationPrincipal Jwt jwt, String orderNumber, String decision, String reason){
        return orderDisputeService.executeAdminReview(jwt,orderNumber,decision,reason);
    }
}
