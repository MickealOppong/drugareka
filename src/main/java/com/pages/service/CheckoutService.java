package com.pages.service;

import com.pages.dto.*;
import com.pages.exception.InvalidOperationException;
import com.pages.model.*;
import com.pages.repository.*;
import com.pages.util.GlobalAddress;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.cfg.Environment;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class CheckoutService {

    private final ListingOrderService listingOrderService;
    private final PaymentService paymentService;
    private final StripePaymentProviderService stripePaymentProviderService;
    private final CartService cartService;
    private final GlobalAddressService globalAddressService;
    private final AppUserDetailsService appUserDetailsService;
    private final PayUService payUService;
    private final MessageSource messageSource;
    private final OrderDisputeRepo orderDisputeRepo;



    @Transactional
    public ResponseDto<String> createCheckout(Jwt jwt) {
        Locale locale = LocaleContextHolder.getLocale();
        try {
            ListingOrder order = listingOrderService.createBuyerOrder(jwt);

            if (order == null ) {

                String msg = messageSource.getMessage("checkout.order.creation_failed", null, "Unable to create order.", locale);
                return ResponseDto.<String>builder()
                        .message(msg)
                        .httpStatus(HttpStatus.UNPROCESSABLE_CONTENT.value())
                        .build();
            }

            if (order.getItems() == null || order.getItems().isEmpty()) {
                String msg = messageSource.getMessage("checkout.cart.empty", null, "Your checkout contains no items.", locale);
                return ResponseDto.<String>builder()
                        .message(msg)
                        .httpStatus(HttpStatus.UNPROCESSABLE_CONTENT.value())
                        .build();
            }

            Session session = stripePaymentProviderService.createEmbeddedConnectCheckoutSession(order, locale.getLanguage());
            return paymentService.createPayment(session, order);

        } catch (StripeException e) {
            log.error("Stripe checkout session creation failed: {}", e.getMessage());
            String msg = messageSource.getMessage("checkout.stripe.session_failed", null, "Unable to create payment session.", locale);
            return ResponseDto.<String>builder()
                    .message(msg)
                    .httpStatus(HttpStatus.BAD_GATEWAY.value())
                    .build();

        } catch (Exception e) {
            log.error("Checkout creation failed", e);
            String msg = messageSource.getMessage("checkout.generic.failed", null, "Unable to create checkout.", locale);
            return ResponseDto.<String>builder()
                    .message(msg)
                    .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
                    .build();
        }
    }


    @Transactional
    public ResponseDto<String> createReturnCheckout(Jwt jwt,Long disputeId) {
        Locale locale = LocaleContextHolder.getLocale();
        try {
           OrderDispute order = orderDisputeRepo.findById(disputeId).orElse(null);

            if (order == null ) {

                String msg = messageSource.getMessage("checkout.order.creation_failed", null, "Unable to create order.", locale);
                return ResponseDto.<String>builder()
                        .message(msg)
                        .httpStatus(HttpStatus.UNPROCESSABLE_CONTENT.value())
                        .build();
            }

            Session session = stripePaymentProviderService.createEmbeddedConnectCheckoutSessionShipmentCost(order,LocaleContextHolder.getLocale().getLanguage());
            return paymentService.createReturnShippingPayment(session, order);

        } catch (StripeException e) {
            log.error("Stripe checkout session creation failed: {}", e.getMessage());
            String msg = messageSource.getMessage("checkout.stripe.session_failed", null, "Unable to create payment session.", locale);
            return ResponseDto.<String>builder()
                    .message(msg)
                    .httpStatus(HttpStatus.BAD_GATEWAY.value())
                    .build();

        } catch (Exception e) {
            log.error("Checkout creation failed", e);
            String msg = messageSource.getMessage("checkout.generic.failed", null, "Unable to create checkout.", locale);
            return ResponseDto.<String>builder()
                    .message(msg)
                    .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
                    .build();
        }
    }

    @Transactional
    public ResponseDto<String> createPayUCheckout(@AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        Locale locale = LocaleContextHolder.getLocale();
        try {
            ListingOrder order = listingOrderService.createBuyerOrder(jwt);

            if (order == null) {
                String msg = messageSource.getMessage("checkout.order.creation_failed", null, "Unable to create order.", locale);
                return ResponseDto.<String>builder()
                        .message(msg)
                        .httpStatus(HttpStatus.UNPROCESSABLE_CONTENT.value())
                        .build();
            }

            if (order.getItems() == null || order.getItems().isEmpty()) {
                String msg = messageSource.getMessage("checkout.cart.empty", null, "Your checkout contains no items.", locale);
                return ResponseDto.<String>builder()
                        .message(msg)
                        .httpStatus(HttpStatus.UNPROCESSABLE_CONTENT.value())
                        .build();
            }

            PayUOrderResponse orderResponse = payUService.payUPaymentRequest(order, request);
            log.info("PayU response {}", orderResponse);

            return paymentService.createPayUPayment(orderResponse, order);

        } catch (Exception e) {
            log.error("Checkout creation failed", e);
            String msg = messageSource.getMessage("checkout.generic.failed", null, "Unable to create checkout.", locale);
            return ResponseDto.<String>builder()
                    .message(msg)
                    .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
                    .build();
        }
    }

    @Transactional
    public ResponseDto<String> buyNow(@AuthenticationPrincipal Jwt jwt, Long[] listingsId) {
        Locale locale = LocaleContextHolder.getLocale();
        if (jwt != null) {
            AppUser appUser = appUserDetailsService.getAppUserByUsername(jwt.getSubject());
            AddressResponse userAddress = globalAddressService.getAddress(appUser);

            if (userAddress == null) {
                String msg = messageSource.getMessage("checkout.address.required", null, "Please add a delivery address to complete your order.", locale);
                return ResponseDto.<String>builder()
                        .message(msg)
                        .httpStatus(HttpStatus.BAD_REQUEST.value())
                        .build();
            }

            Boolean isAddToCart = (Boolean) cartService.addItemToCart(listingsId, jwt).getData();

            if (Boolean.TRUE.equals(isAddToCart)) {
                return createCheckout(jwt);
            }

            String msg = messageSource.getMessage("cart.item.reserved", null, "Oops error, could not complete purchase order.", locale);
            return ResponseDto.<String>builder()
                    .message(msg)
                    .httpStatus(HttpStatus.FORBIDDEN.value())
                    .build();
        }

        String msg = messageSource.getMessage("checkout.auth.required", null, "Something went wrong, please authenticate and try again.", locale);
        return ResponseDto.<String>builder()
                .message(msg)
                .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .build();
    }

    @Transactional
    public ResponseDto<String> buyNowBuyerOffer(@AuthenticationPrincipal Jwt jwt, Long[] listingsId,BigDecimal offer) {
    try {
        Locale locale = LocaleContextHolder.getLocale();
        if (jwt != null) {
            AppUser appUser = appUserDetailsService.getAppUserByUsername(jwt.getSubject());
            AddressResponse userAddress = globalAddressService.getAddress(appUser);

            if (userAddress == null) {
                String msg = messageSource.getMessage("checkout.address.required", null, "Please add a delivery address to complete your order.", locale);
                return ResponseDto.<String>builder()
                        .message(msg)
                        .httpStatus(HttpStatus.BAD_REQUEST.value())
                        .build();
            }

            Boolean isAddToCart = (Boolean) cartService.addItemToCartBuyerOffer(listingsId, jwt,offer).getData();

            if (Boolean.TRUE.equals(isAddToCart)) {
                return createCheckout(jwt);
            }

            String msg = messageSource.getMessage("cart.item.reserved", null, "Oops error, could not complete purchase order.", locale);
            return ResponseDto.<String>builder()
                    .message(msg)
                    .httpStatus(HttpStatus.FORBIDDEN.value())
                    .build();
        }

        String msg = messageSource.getMessage("checkout.auth.required", null, "Something went wrong, please authenticate and try again.", locale);
        return ResponseDto.<String>builder()
                .message(msg)
                .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .build();
    }catch (Exception e){
        return ResponseDto.<String>builder()
                .message(e.getMessage())
                .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .build();
    }
    }

    @Transactional
    public ResponseDto<String> returnShipping(@AuthenticationPrincipal Jwt jwt,Long disputeId) {
        try {
            Locale locale = LocaleContextHolder.getLocale();
            if (jwt != null) {

                    return createReturnCheckout(jwt,disputeId);
            }

            String msg = messageSource.getMessage("checkout.auth.required", null, "Something went wrong, please authenticate and try again.", locale);
            return ResponseDto.<String>builder()
                    .message(msg)
                    .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
                    .build();
        }catch (Exception e){
            return ResponseDto.<String>builder()
                    .message(e.getMessage())
                    .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
                    .build();
        }
    }

    @Transactional
    public ResponseDto<String> payUBuyNow(@AuthenticationPrincipal Jwt jwt, Long[] listingsId, HttpServletRequest request) {
        Locale locale = LocaleContextHolder.getLocale();
        if (jwt != null) {
            AppUser appUser = appUserDetailsService.getAppUserByUsername(jwt.getSubject());
            AddressResponse userAddress = globalAddressService.getAddress(appUser);

            if (userAddress == null) {
                String msg = messageSource.getMessage("checkout.address.required", null, "Please add a delivery address to complete your order.", locale);
                return ResponseDto.<String>builder()
                        .message(msg)
                        .httpStatus(HttpStatus.BAD_REQUEST.value())
                        .build();
            }

            Boolean isAddToCart = (Boolean) cartService.addItemToCart(listingsId, jwt).getData();

            if (Boolean.TRUE.equals(isAddToCart)) {
                return createPayUCheckout(jwt, request);
            }

            String msg = messageSource.getMessage("checkout.action.failed", null, "Oops error, could not complete purchase order.", locale);
            return ResponseDto.<String>builder()
                    .message(msg)
                    .httpStatus(HttpStatus.FORBIDDEN.value())
                    .build();
        }

        String msg = messageSource.getMessage("checkout.auth.required", null, "Something went wrong, please authenticate and try again.", locale);
        return ResponseDto.<String>builder()
                .message(msg)
                .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .build();
    }
}
