package com.pages.service;

import com.pages.dto.CartResponse;
import com.pages.dto.CheckoutResponse;
import com.pages.dto.ListingOrderResponse;
import com.pages.dto.ResponseDto;
import com.pages.model.*;
import com.pages.repository.*;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Slf4j
@Service
public class CheckoutService {



    private final ListingOrderService listingOrderService;
    private final PaymentService paymentService;
    private final StripePaymentProviderService stripePaymentProviderService;
    private final CartService cartService;

    public CheckoutService(ListingOrderService listingOrderService,
                           PaymentService paymentService,
                           StripePaymentProviderService stripePaymentProviderService, CartService cartService) {
        this.listingOrderService = listingOrderService;
        this.paymentService = paymentService;
        this.stripePaymentProviderService = stripePaymentProviderService;
        this.cartService = cartService;
    }



    @Transactional
    public ResponseDto<String> createCheckout(Jwt jwt) {



        try {

            ListingOrder order =
                    listingOrderService.createBuyerOrder(jwt);

            if (order == null) {
                return ResponseDto.<String>builder()
                        .message("Unable to create order")
                        .httpStatus(HttpStatus.UNPROCESSABLE_CONTENT.value()).build();
            }

            if (order.getItems() == null ||
                    order.getItems().isEmpty()) {

                return ResponseDto.<String>builder()
                        .message("Your checkout contains no items.")
                        .httpStatus(HttpStatus.UNPROCESSABLE_CONTENT.value())
                        .build();
            }

            Session session =
                    stripePaymentProviderService
                            .createEmbeddedCheckoutSession(order);

            return paymentService.createPayment(session, order);

        } catch (StripeException e) {

            log.error("Stripe checkout session creation failed", e);

            return ResponseDto.<String>builder()
                    .message("Unable to create payment session.")
                    .httpStatus(HttpStatus.BAD_GATEWAY.value())
                    .build();

        } catch (Exception e) {

            log.error("Checkout creation failed", e);

            return ResponseDto.<String>builder()
                    .message("Unable to create checkout.")
                    .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
                    .build();
        }
    }



}