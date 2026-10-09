package com.pages.controller;

import com.pages.dto.PayUNotification;
import com.pages.enums.PaymentStatus;
import com.pages.service.PayUService;
import com.pages.service.PaymentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@RestController
@RequestMapping("/api/payment/payu")
public class PaymentController {

    private final PaymentService paymentService;
    private final PayUService payUService;

    public PaymentController(PaymentService paymentService, PayUService payUService) {
        this.paymentService = paymentService;
        this.payUService = payUService;
    }

    @GetMapping("/status")
    public ResponseEntity<PaymentStatus> getStatus( String orderNumber){
        return ResponseEntity.ok( paymentService.getPaymentStatus(orderNumber.trim()));
    }

    @PostMapping("/notify")
    public void payuNotify(@RequestBody String rawBody,
                                           @RequestHeader("OpenPayu-Signature") String signature) {

        try {

            // 1. Verify exact PayU request
            payUService.verifyNotificationSignature(rawBody, signature);

            // 2. Convert JSON to your DTO
            ObjectMapper objectMapper = new ObjectMapper();
            PayUNotification notification =
                    objectMapper.readValue(rawBody, PayUNotification.class);

            // 3. Process notification
            paymentService.handleNotification(notification);
        } catch (Exception e) {

            log.error("payU processing failed", e);
        }
    }
}
