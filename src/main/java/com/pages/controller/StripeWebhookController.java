package com.pages.controller;

import com.pages.enums.InventoryStatus;
import com.pages.enums.OrderStatus;
import com.pages.model.InventoryItem;
import com.pages.model.ListingOrder;
import com.pages.model.ListingOrderItem;
import com.pages.service.PaymentService;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
@Slf4j
public class StripeWebhookController {

    private final PaymentService paymentService;

    @Value("${stripe.webhook-secret}")
    private String webhookSecret;

    @PostMapping("/stripe")
    public ResponseEntity<String> webhook(@RequestBody String payload, @RequestHeader("Stripe-Signature") String signature) {

        final Event event;

        try {

            event = Webhook.constructEvent(payload, signature, webhookSecret);

        } catch (Exception e) {

            log.error("Invalid Stripe webhook", e);

            return ResponseEntity
                    .badRequest()
                    .body("Invalid webhook");
        }

        try {

            switch (event.getType()) {

                case "checkout.session.completed":
                    paymentService.handleCheckoutCompleted(event);
                    break;

                case "checkout.session.expired":

                    paymentService.handleCheckoutExpired(event);

                    break;

                default:

                    log.debug("Unhandled Stripe event: {}", event.getType());
            }

            return ResponseEntity.ok("received");

        } catch (Exception e) {

            log.error("Stripe webhook processing failed", e);

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Webhook processing failed");
        }
    }
}