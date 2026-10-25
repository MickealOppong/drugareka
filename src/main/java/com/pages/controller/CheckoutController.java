package com.pages.controller;

import com.pages.dto.ResponseDto;
import com.pages.service.CheckoutService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@Slf4j
@RestController
@RequestMapping("/api/checkout")
@RequiredArgsConstructor
public class CheckoutController {

    private final CheckoutService checkoutService;

    @PostMapping("stripe")
    public ResponseDto<String> createCheckout(@AuthenticationPrincipal Jwt jwt) {
        return checkoutService.createCheckout(jwt);
    }

    @PreAuthorize("hasAuthority('ROLE_USER')")
    @PostMapping("/stripe-buy-now")
    public ResponseDto<String> buyNow(@AuthenticationPrincipal Jwt jwt,Long[] listingId) {
        return checkoutService.buyNow(jwt,listingId);
    }

    @PreAuthorize("hasAuthority('ROLE_USER')")
    @PostMapping("/stripe-offer-buy-now")
    public ResponseDto<String> offerBuyNow(@AuthenticationPrincipal Jwt jwt, Long[] listingId, BigDecimal offer) {
        return checkoutService.buyNowBuyerOffer(jwt,listingId,offer);
    }

    @PostMapping("/return-shipping")
    public ResponseDto<String> checkoutShippingCost(@AuthenticationPrincipal Jwt jwt,Long disputeId) {
        return checkoutService.createReturnCheckout(jwt,disputeId);
    }


    @PostMapping("/payU")
    public ResponseDto<String> createPayUCheckout(@AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        return checkoutService.createPayUCheckout(jwt,request);
    }

    @PreAuthorize("hasAuthority('ROLE_USER')")
    @PostMapping("/payU-buy-now")
    public ResponseDto<String> payUBuyNowCheckout(@AuthenticationPrincipal Jwt jwt,Long[] listingId,HttpServletRequest request) {
        return checkoutService.payUBuyNow(jwt,listingId,request);
    }
}
