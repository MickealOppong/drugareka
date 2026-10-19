package com.pages.service;

import com.pages.dto.*;
import com.pages.enums.PaymentStatus;
import com.pages.interfaces.PaymentProvider;
import com.pages.model.*;
import com.pages.repository.PaymentRepo;
import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Transfer;
import com.stripe.model.checkout.*;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.TransferCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
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




    public Transfer transferSellerProceeds(String connectedAccountId, String orderNumber, long amountInGrosze) throws StripeException {

        if (connectedAccountId == null
                || connectedAccountId.isBlank()) {
            throw new IllegalArgumentException(
                    "Seller has no connected Stripe account."
            );
        }

        if (amountInGrosze <= 0) {
            throw new IllegalArgumentException(
                    "Transfer amount must be positive.");
        }

        TransferCreateParams params =
                TransferCreateParams.builder()
                        .setAmount(amountInGrosze)
                        .setCurrency("pln")
                        .setDestination(connectedAccountId)
                        .setTransferGroup(orderNumber)
                        .putMetadata("orderNumber", orderNumber)
                        .build();

        return Transfer.create(params);
    }


    public Session createEmbeddedConnectCheckoutSession(
            ListingOrder order,
            String userLocale
            ) throws Exception {
        try {
            if (order == null
                    || order.getItems() == null
                    || order.getItems().isEmpty()) {
                throw new IllegalArgumentException(
                        "Cannot create checkout for an empty order."
                );
            }

            String locale = userLocale == null
                    ? "pl"
                    : userLocale.trim().toLowerCase(Locale.ROOT);

            SessionCreateParams.Locale stripeLocale =
                    switch (locale) {
                        case "pl" -> SessionCreateParams.Locale.PL;
                        case "en" -> SessionCreateParams.Locale.EN;
                        default -> SessionCreateParams.Locale.AUTO;
                    };

            if (successUrl == null || successUrl.isBlank()) {
                throw new IllegalStateException(
                        "Stripe Checkout return URL is not configured."
                );
            }

            URI returnUri = URI.create(successUrl.trim());

            if (!"https".equalsIgnoreCase(returnUri.getScheme())
                    || returnUri.getHost() == null
                    || returnUri.getUserInfo() != null) {
                throw new IllegalStateException(
                        "Stripe Checkout return URL must be a valid HTTPS URL."
                );
            }

            String submitMessage = "pl".equals(locale)
                    ? "Zapłać i zabezpiecz zamówienie"
                    : "Pay and secure your order";

            //receipt email
            String buyerEmail = order.getBuyer().getUsername();

            //stripe seller account for onBehalfOf field

            Set<Long> uniqueSellerIds = order.getItems().stream()
                    .map(ListingOrderItem::getSeller)
                    .map(SellerProfile::getId)
                    .collect(Collectors.toSet());

            // If the size of the set is exactly 1, every item belongs to the same seller!
            boolean isSameSeller = uniqueSellerIds.size() == 1;

            String sellerAccountId = isSameSeller
                    ? order.getItems().get(0).getSeller().getStripeAccountId()
                    : null;

            SessionCreateParams.CustomText customText =
                    SessionCreateParams.CustomText.builder()
                            .setSubmit(
                                    SessionCreateParams.CustomText.Submit
                                            .builder()
                                            .setMessage(submitMessage)
                                            .build()
                            )
                            .build();

            SessionCreateParams.Builder params =
                    SessionCreateParams.builder()
                            .setUiMode(
                                    SessionCreateParams.UiMode.HOSTED_PAGE
                            )
                            .setMode(
                                    SessionCreateParams.Mode.PAYMENT
                            )
                            .setSuccessUrl(successUrl
                                    + "/success?session_id={CHECKOUT_SESSION_ID}")
                            .setCustomText(customText)
                            .setLocale(stripeLocale)
                            .setExpiresAt(
                                    Instant.now()
                                            .plus(30, ChronoUnit.MINUTES)
                                            .getEpochSecond()
                            )
                            .setPaymentIntentData(SessionCreateParams.PaymentIntentData.builder().setTransferGroup(order.getOrderNumber())
                                    .setReceiptEmail(buyerEmail)
                                    .setOnBehalfOf(sellerAccountId)
                                    .build()

                            )
                            .setClientReferenceId(order.getOrderNumber())
                            .setIntegrationIdentifier("web_kasoa")
                            .putMetadata(
                                    "orderId",
                                    Objects.requireNonNull(
                                            order.getId(),
                                            "Order ID must be persisted before checkout."
                                    ).toString()
                            );

            for (ListingOrderItem item : order.getItems()) {
                if (item == null) {
                    throw new IllegalArgumentException(
                            "Order contains a null item."
                    );
                }

                BigDecimal sellerPrice = item.getSellerPrice().add(item.getShippingCost()).add(item.getServiceCharge());

                if (sellerPrice.signum() <= 0) {
                    throw new IllegalArgumentException(
                            "A valid positive price is required for order item "
                                    + item.getId()
                    );
                }

                // PLN has two decimal places. Reject invalid precision instead
                // of silently truncating fractional grosze.
                long amountInGrosze = sellerPrice
                        .setScale(2, RoundingMode.UNNECESSARY)
                        .movePointRight(2)
                        .longValueExact();

                String productName = item.getProductNameSnapshot();

                SessionCreateParams.LineItem.PriceData.ProductData productData =
                        SessionCreateParams.LineItem.PriceData.ProductData
                                .builder()
                                .setName(productName)
                                .build();

                SessionCreateParams.LineItem.PriceData priceData =
                        SessionCreateParams.LineItem.PriceData.builder()
                                .setCurrency("pln")
                                .setUnitAmount(amountInGrosze)
                                .setProductData(productData)
                                .build();

                SessionCreateParams.LineItem lineItem =
                        SessionCreateParams.LineItem.builder()
                                .setPriceData(priceData)
                                .setQuantity(1L)
                                .build();

                params.addLineItem(lineItem);
            }

          return  Session.create(params.build());
        } catch (StripeException e) {
            log.error(
                    "Stripe Checkout session creation failed. Code: {}, Message: {}",
                    e.getCode(),
                    e.getMessage(),
                    e
            );

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Stripe Checkout session creation failed.",
                    e
            );

        } catch (IllegalArgumentException | IllegalStateException e) {
            log.warn("Invalid Checkout session configuration or order: {}",
                    e.getMessage());

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    e.getMessage(),
                    e
            );

        } catch (Exception e) {
            log.error("Unexpected Checkout session creation failure.", e);

            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to create Checkout session.",
                    e
            );
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
