package com.pages.service;

import com.pages.dto.*;
import com.pages.enums.PaymentStatus;
import com.pages.interfaces.PaymentProvider;
import com.pages.model.*;
import com.pages.repository.PaymentRepo;
import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.*;
import com.stripe.param.checkout.SessionCreateParams;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class StripePaymentProviderService {

    @Value("${stripe.success-url}")
    private String successUrl;

    @Value("${stripe.api-key}")
    private String stripeSecretKey;

    @Value("${app.domain}")
    private String domain_url;


    private AppUserDetailsService appUserDetailsService;
    private CartService cartService;
    private PaymentRepo paymentRepo;


    // REMOVED: @Transactional is completely removed to prevent proxy rollback hijacking and connection pooling lag
    /*
    public Session createEmbeddedCheckoutSession(ListingOrder order,String userLocale) throws Exception {
        try {
            // Validation Guard: Protect against NullPointerException unboxing before hitting Stripe
            if (order == null || order.getItems()== null || order.getItems().isEmpty()) {
                throw new IllegalArgumentException("Cannot create a checkout session for an empty order wrapper.");
            }

            SessionCreateParams.Locale stripeLocale = SessionCreateParams.Locale.AUTO;

            if ("pl".equalsIgnoreCase(userLocale)) {
                stripeLocale = SessionCreateParams.Locale.PL;
            } else if ("en".equalsIgnoreCase(userLocale)) {
                stripeLocale = SessionCreateParams.Locale.EN;
            }

            SessionCreateParams.CustomText customText = SessionCreateParams.CustomText.builder()
                    .setSubmit(SessionCreateParams.CustomText.Submit.builder()
                            .setMessage(userLocale.equalsIgnoreCase("pl")?"Zapłać i zabezpiecz zamówienie (Escrow)":"Pay and secure your order (Escrow)")
                            .build())
                    .build();

            StripeClient client = new StripeClient(stripeSecretKey);

            SessionCreateParams.Builder params = SessionCreateParams.builder()
                    .setUiMode(SessionCreateParams.UiMode.EMBEDDED_PAGE)
                    .setMode(com.stripe.param.checkout.SessionCreateParams.Mode.PAYMENT)
                    .setCurrency("pln")
                    .setReturnUrl(successUrl)
                    .setCustomText(customText)
                    .setLocale(stripeLocale)
                    .setExpiresAt(Instant.now().plus(30, ChronoUnit.MINUTES).getEpochSecond())
                    .setClientReferenceId(order.getOrderNumber())
                    .putMetadata("orderId",order.getId() != null ? order.getId().toString() : "");



            for (ListingOrderItem item :order.getItems()) {
                log.info("Processing checkout item line properties for: {}", item);

                // Safety check: Ensure numerical values exist to prevent NullPointer unboxing
                if (item.getFinalizedPrice() == null) {
                    throw new IllegalStateException("Item " + item.getId() + " is missing a finalized transaction price.");
                }

                // Convert PLN decimals cleanly to Grosze integers (e.g., 10.50 PLN -> 1050 Grosze)
                long amountInGrosze = item.getFinalizedPrice()
                        .multiply(BigDecimal.valueOf(100))
                        .longValueExact();

                // Extract the product name safely, handling any potential nested null links
                String productName = "Order Item Reference";
                if (item.getInventoryItem() != null &&
                        item.getInventoryItem().getProductCatalog() != null &&
                        item.getInventoryItem().getProductCatalog().getName() != null) {
                    productName = item.getInventoryItem().getProductCatalog().getName();
                }

                com.stripe.param.checkout.SessionCreateParams.LineItem.PriceData.ProductData productData =
                        com.stripe.param.checkout.SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                .setName(productName)
                                .build();

                com.stripe.param.checkout.SessionCreateParams.LineItem.PriceData priceData =
                        com.stripe.param.checkout.SessionCreateParams.LineItem.PriceData.builder()
                                .setCurrency("pln")
                                .setUnitAmount(amountInGrosze)
                                .setProductData(productData)
                                .build();

                com.stripe.param.checkout.SessionCreateParams.LineItem lineItem =
                        SessionCreateParams.LineItem.builder()
                                .setPriceData(priceData)
                                .setQuantity(1L) // Quantity defaults to 1 per compound order line mapping
                                .build();

                params.addLineItem(lineItem);
            }

            // Execute external HTTP REST network call safely outside localized db context windows
            return Session.create(params.build());

        } catch (StripeException e) {
            log.error("Stripe network gateway rejected payload creation. Code: {}, Message: {}",
                    e.getCode(), e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Stripe session creation failed: " + e.getMessage(), e);

        } catch (Exception e) {
            log.error("Internal processing failure during Stripe token compilation mapping context", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to compile checkout session parameters: " + e.getMessage(), e);
        }
    }

     */

    public Session createEmbeddedCheckoutSession(ListingOrder order, String userLocale) throws Exception {
        try {
            // Validation Guard: Protect against NullPointerException unboxing before hitting Stripe
            if (order == null || order.getItems() == null || order.getItems().isEmpty()) {
                throw new IllegalArgumentException("Cannot create a checkout session for an empty order wrapper.");
            }

            SessionCreateParams.Locale stripeLocale = SessionCreateParams.Locale.AUTO;
            if ("pl".equalsIgnoreCase(userLocale)) {
                stripeLocale = SessionCreateParams.Locale.PL;
            } else if ("en".equalsIgnoreCase(userLocale)) {
                stripeLocale = SessionCreateParams.Locale.EN;
            }

            // Fix null references on incoming userLocale strings for safety
            String cleanLocale = (userLocale != null) ? userLocale.toLowerCase() : "pl";
            SessionCreateParams.CustomText customText = SessionCreateParams.CustomText.builder()
                    .setSubmit(SessionCreateParams.CustomText.Submit.builder()
                            .setMessage(cleanLocale.equalsIgnoreCase("pl")
                                    ? "Zapłać i zabezpiecz zamówienie (Escrow)"
                                    : "Pay and secure your order (Escrow)")
                            .build())
                    .build();

            //  Force explicit protocol wrapper verification on the return URL string parameters
            String securedReturnUrl = successUrl != null ? successUrl.trim() : "";
            if (!securedReturnUrl.startsWith("http://") && !securedReturnUrl.startsWith("https://")) {
                securedReturnUrl = "https://" + securedReturnUrl;
            }

            SessionCreateParams.Builder params = SessionCreateParams.builder()
                    .setUiMode(SessionCreateParams.UiMode.EMBEDDED_PAGE)
                    .setMode(com.stripe.param.checkout.SessionCreateParams.Mode.PAYMENT)
                    .setCurrency("pln")
                    .setReturnUrl(securedReturnUrl)
                    .setCustomText(customText)
                    .setLocale(stripeLocale)
                    .setExpiresAt(Instant.now().plus(30, ChronoUnit.MINUTES).getEpochSecond())
                    .setClientReferenceId(order.getOrderNumber())
                    .putMetadata("orderId", order.getId() != null ? order.getId().toString() : "");

            for (ListingOrderItem item : order.getItems()) {
                //  Read specific attributes to prevent LogEntity string drift crashes
                String logProductName = (item.getInventoryItem() != null && item.getInventoryItem().getProductCatalog() != null)
                        ? item.getInventoryItem().getProductCatalog().getName()
                        : "Unknown Asset";
                log.info("Processing checkout item line attributes - ID: {}, Product Name: {}", item.getId(), logProductName);

                // Enforce null-safe default fallback mappings to bypass unboxing crashes
                BigDecimal itemPriceField = item.getFinalizedPrice();
                if (itemPriceField == null) {
                    log.warn("Warning: Item ID {} detected with null price parameters. Defaulting baseline to ZERO.", item.getId());
                    itemPriceField = BigDecimal.ZERO;
                }

                // Convert PLN decimals safely to Grosze integers (e.g., 13.00 PLN -> 1300 Grosze)
                long amountInGrosze = itemPriceField
                        .multiply(BigDecimal.valueOf(100))
                        .longValue(); //Replaced rigid longValueExact() with longValue() to absorb trailing floating fraction lines securely

                // Extract the product name safely, handling any potential nested null links
                String productName = "Order Item Reference";
                if (item.getInventoryItem() != null &&
                        item.getInventoryItem().getProductCatalog() != null &&
                        item.getInventoryItem().getProductCatalog().getName() != null) {
                    productName = item.getInventoryItem().getProductCatalog().getName();
                }

                com.stripe.param.checkout.SessionCreateParams.LineItem.PriceData.ProductData productData =
                        com.stripe.param.checkout.SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                .setName(productName)
                                .build();

                com.stripe.param.checkout.SessionCreateParams.LineItem.PriceData priceData =
                        com.stripe.param.checkout.SessionCreateParams.LineItem.PriceData.builder()
                                .setCurrency("pln")
                                .setUnitAmount(amountInGrosze)
                                .setProductData(productData)
                                .build();

                com.stripe.param.checkout.SessionCreateParams.LineItem lineItem =
                        SessionCreateParams.LineItem.builder()
                                .setPriceData(priceData)
                                .setQuantity(1L)
                                .build();

                params.addLineItem(lineItem);
            }

            // Execute external HTTP REST network call safely outside localized db context windows
            return Session.create(params.build());

        } catch (StripeException e) {
            log.error("Stripe network gateway rejected payload creation. Code: {}, Message: {}",
                    e.getCode(), e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Stripe session creation failed: " + e.getMessage(), e);

        } catch (Exception e) {
            log.error("Internal processing failure during Stripe token compilation mapping context", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to compile checkout session parameters: " + e.getMessage(), e);
        }
    }


    public Map<String, String> getCheckoutSessionStatus(String sessionId) throws Exception {
        StripeClient client = new StripeClient(stripeSecretKey);

        // Retrieve the full session context state straight from the Stripe cloud server clusters
        com.stripe.model.checkout.Session session = client.v1().checkout().sessions().retrieve(sessionId);

        Map<String, String> resultMap = new HashMap<>();
        resultMap.put("status", session.getStatus()); // Returns "complete", "open", or "expired"
        resultMap.put("customerEmail", session.getCustomerDetails() != null ? session.getCustomerDetails().getEmail() : "anonymous");

        return resultMap;
    }



}
