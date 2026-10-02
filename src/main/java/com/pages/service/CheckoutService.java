package com.pages.service;

import com.pages.dto.*;
import com.pages.exception.InvalidOperationException;
import com.pages.model.*;
import com.pages.repository.*;
import com.pages.util.GlobalAddress;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.cfg.Environment;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Locale;

@Slf4j
@Service
public class CheckoutService {



    private final ListingOrderService listingOrderService;
    private final PaymentService paymentService;
    private final StripePaymentProviderService stripePaymentProviderService;
    private final CartService cartService;
    private final GlobalAddressService globalAddressService;
    private final AppUserDetailsService appUserDetailsService;
    private final PayUService payUService;

    public CheckoutService(ListingOrderService listingOrderService,
                           PaymentService paymentService,
                           StripePaymentProviderService stripePaymentProviderService, CartService cartService, GlobalAddressService globalAddressService, AppUserDetailsService appUserDetailsService, PayUService payUService) {
        this.listingOrderService = listingOrderService;
        this.paymentService = paymentService;
        this.stripePaymentProviderService = stripePaymentProviderService;
        this.cartService = cartService;
        this.globalAddressService = globalAddressService;
        this.appUserDetailsService = appUserDetailsService;
        this.payUService = payUService;
    }



    @Transactional
    public ResponseDto<String> createCheckout(@AuthenticationPrincipal Jwt jwt) {

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

            Locale locale =LocaleContextHolder.getLocale();
            Session session =
                    stripePaymentProviderService
                            .createEmbeddedCheckoutSession(order,locale.getLanguage());

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

    @Transactional
    public ResponseDto<String> createPayUCheckout(@AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {

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

               PayUOrderResponse orderResponse= payUService.payUPaymentRequest(order,request);

            log.info("PayU response {}",orderResponse);

          return paymentService.createPayUPayment(orderResponse,order);


        } catch (Exception e) {

            log.error("Checkout creation failed", e);

            return ResponseDto.<String>builder()
                    .message("Unable to create checkout.")
                    .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
                    .build();
        }
    }

@Transactional
public  ResponseDto<String> buyNow(@AuthenticationPrincipal Jwt jwt, Long[] listingsId){
        if(jwt!=null){

            AppUser appUser = appUserDetailsService.getAppUserByUsername(jwt.getSubject());
           AddressResponse userAddress= globalAddressService.getAddress(appUser);
           if(userAddress==null){
               return ResponseDto.<String>builder()
                       .message("Proszę podać adres dostawy, aby sfinalizować zakupy"+
                               "\nPlease add address to complete order")
                       .httpStatus(HttpStatus.BAD_REQUEST.value())
                       .build();

           }
            Boolean isAddToCart= (Boolean) cartService.addItemToCart(listingsId,jwt).getData();

            if(isAddToCart){
                return createCheckout(jwt);
            }
            return ResponseDto.<String>builder()
                    .message( "Oops błąd, nie udało się sfinalizować zakupy."+
                            "\n Oops error, could not complete order")
                    .httpStatus(HttpStatus.FORBIDDEN.value())
                    .build();
        }
    return ResponseDto.<String>builder()
            .message( "Błąd podczas finalizowania zamówienia, spróbuj ponownie"+
                    "Something went wrong, please try again")
            .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .build();

}

    @Transactional
    public  ResponseDto<String> payUBuyNow(@AuthenticationPrincipal Jwt jwt, Long[] listingsId,HttpServletRequest request){
        if(jwt!=null){

            AppUser appUser = appUserDetailsService.getAppUserByUsername(jwt.getSubject());
            AddressResponse userAddress= globalAddressService.getAddress(appUser);
            if(userAddress==null){
                return ResponseDto.<String>builder()
                        .message("Proszę podać adres dostawy, aby sfinalizować zakupy"+
                                "\nPlease add address to complete order")
                        .httpStatus(HttpStatus.BAD_REQUEST.value())
                        .build();

            }
            Boolean isAddToCart= (Boolean) cartService.addItemToCart(listingsId,jwt).getData();

            if(isAddToCart){
                return createPayUCheckout(jwt,request);
            }
            return ResponseDto.<String>builder()
                    .message( "Oops błąd, nie udało się sfinalizować zakupy."+
                            "\n Oops error, could not complete order")
                    .httpStatus(HttpStatus.FORBIDDEN.value())
                    .build();
        }
        return ResponseDto.<String>builder()
                .message( "Błąd podczas finalizowania zamówienia, spróbuj ponownie"+
                        "Something went wrong, please try again")
                .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .build();

    }

}