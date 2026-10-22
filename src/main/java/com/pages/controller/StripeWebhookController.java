package com.pages.controller;

import com.pages.enums.InventoryStatus;
import com.pages.enums.OrderStatus;
import com.pages.model.InventoryItem;
import com.pages.model.ListingOrder;
import com.pages.model.ListingOrderItem;
import com.pages.service.PaymentService;
import com.pages.service.SellerPayoutService;
import com.pages.service.StripeConnectService;
import com.stripe.StripeClient;
import com.stripe.events.V2CoreAccountUpdatedEvent;
import com.stripe.events.V2CoreAccountUpdatedEventNotification;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.model.v2.core.EventNotification;
import com.stripe.net.Webhook;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.interceptor.NamedCacheResolver;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
@Slf4j
public class StripeWebhookController {

    private final PaymentService paymentService;
    private final StripeConnectService stripeConnectService;
    private  final StripeClient client;
    private final ObjectMapper objectMapper;
    private final SellerPayoutService sellerPayoutService;

    @Value("${stripe.webhook-secret}")
    private String webhookSecret;

    @PostMapping("/stripe")
    public ResponseEntity<String> webhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String signature) {

        try {
            JsonNode root = objectMapper.readTree(payload);
            String objectType = root.path("object").asString();



            if ("v2.core.event".equals(objectType)) {
                EventNotification notification =
                        client.parseEventNotification(
                                payload, signature, webhookSecret);

                switch (notification.getType()) {
                    case "v2.core.account[identity].updated":
                    case "v2.core.account_person.updated":
                        stripeConnectService.handleAccountUpdated(notification);
                        break;

                    default:
                        log.debug("Unhandled Stripe thin event: {}",
                                notification.getType());
                }

            } else {
                Event event = Webhook.constructEvent(
                        payload, signature, webhookSecret);

                switch (event.getType()) {
                    case "checkout.session.completed":
                    case "checkout.session.async_payment_succeeded":
                        paymentService.handleCheckoutCompleted(event);
                        break;
                    case "charge.refunded":
                        paymentService.handleRefundSuccess(event);
                        break;
                    case "checkout.session.expired":
                        paymentService.handleCheckoutSessionExpired(event);
                        break;
                        case "payment_intent.succeeded":
                            paymentService.handleDelayedPaymentIntentSuccess(event);
                            break;
                    case "checkout.session.async_payment_failed":
                         case "payment_intent.failed":
                        paymentService.handleAsyncPaymentFailed(event);
                        break;
                    case "transfer.created":
                       sellerPayoutService.handleTransferCreated(event);
                        break;
                    // Direct processing tracking for independent charge instances
                    case "charge.succeeded":
                        paymentService.handleChargeSucceeded(event);
                    break;

                    case "transfer.reversed":
                        sellerPayoutService.handleTransferReversed(event);
                        break;
                    default:
                        log.debug("Unhandled Stripe event: {}", event.getType());
                        break;
                }
            }

            return ResponseEntity.ok("received");

        } catch (Exception e) {
            log.error("Stripe webhook processing failed :{}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Webhook processing failed");
        }
    }
}