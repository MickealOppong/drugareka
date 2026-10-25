package com.pages.controller;

import com.pages.dto.ListPagePayout;
import com.pages.dto.ResponseDto;
import com.pages.dto.SellerPayoutResponse;
import com.pages.service.PayoutProcessingService;
import com.pages.service.SellerPayoutService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/payouts")
@RequiredArgsConstructor
public class PayoutController {


    private final SellerPayoutService sellerPayoutService;



    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @GetMapping("seller-payouts")
    public ListPagePayout payOuts(@AuthenticationPrincipal Jwt jwt, Integer page, Integer size){
       return sellerPayoutService.allPayouts(jwt,page,size);
    }

    @PreAuthorize(("hasAuthority('ROLE_ADMIN')"))
    @PostMapping("/status")
    public ResponseDto<Object> settleSeller(@AuthenticationPrincipal Jwt jwt,
                                            @RequestParam String status,
                                            @RequestParam Long payoutId){
        return sellerPayoutService.updateStatus(jwt,payoutId,status);
    }
}
