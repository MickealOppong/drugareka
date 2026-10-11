package com.pages.controller;
import com.pages.service.StripeConnectService;
import com.stripe.exception.StripeException;
import com.stripe.model.v2.core.Account;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/seller/stripe")
@RequiredArgsConstructor
public class StripeConnectController {

    private final StripeConnectService stripeConnectService;


    /**
     * Creates a Stripe connected account if one does not already exist,
     * then returns a Stripe-hosted onboarding URL.
     */
    @PostMapping("/onboarding")
    public ResponseEntity<?> startOnboarding(
            @AuthenticationPrincipal Jwt jwt) {
        return stripeConnectService.startOnboarding(jwt);
    }




}