package com.pages.service;

import com.pages.dto.*;
import com.pages.enums.PaymentStatus;
import com.pages.model.ListingOrder;
import com.pages.util.PayUProperties;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
public class PayUService {

    private final RestClient restClient;
    private final PayUProperties payUProperties;



    public PayUService(RestClient.Builder restClientBuilder, PayUProperties payUProperties) {
        this.payUProperties = payUProperties;

        //  Anchor baseUrl directly to the client instance
        String targetDomain = (payUProperties.getBaseUrl() != null && !payUProperties.getBaseUrl().isBlank())
                ? payUProperties.getBaseUrl()
                : "https://secure.snd.payu.com";

        this.restClient = restClientBuilder.baseUrl(targetDomain).build();

    }


    public PayUOrderResponse payUPaymentRequest(ListingOrder order, HttpServletRequest servletRequest) {

        //order number
        String orderNumber = order.getOrderNumber();
        //order total
        BigDecimal totalAmount =order.getOrderTotal();
        //products
        List<PayUProduct> products = order.getItems().stream().map(item->{

            BigDecimal unitPrice =item.getSellerPrice().add(item.getShippingCost()).add(item.getServiceCharge())
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(0, RoundingMode.HALF_UP);

            return PayUProduct.builder()
                    .name(item.getProductNameSnapshot())
                    .unitPrice(unitPrice)
                    .quantity(1L)
                    .build();

        }).toList();



        //language
        String locale = LocaleContextHolder.getLocale().getLanguage();

        //buyer
        PayUBuyer buyer = new PayUBuyer();
        buyer.setEmail(order.getBuyer().getUsername());
        buyer.setEmail(order.getBuyerNameSnapshot());
        buyer.setLanguage(locale);

        //access token JWT
        String accessToken = getAccessToken();

        PayURequest request = new PayURequest();

        request.setNotifyUrl(payUProperties.getNotifyUrl());
        request.setContinueUrl(payUProperties.getContinueUrl() + "?orderNumber=" + orderNumber);
        request.setCustomerIp(servletRequest.getRemoteAddr());
        request.setMerchantPosId(payUProperties.getPosId());
        request.setDescription("Kasoa.pl Order #" + orderNumber);
        request.setCurrencyCode("PLN");


        // PayU expects the amount in grosz
        request.setTotalAmount(
                totalAmount
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(0, RoundingMode.HALF_UP)
                        .toPlainString()
        );

        request.setExtOrderId(orderNumber);

        request.setBuyer(buyer);
        request.setProducts(products);

        return restClient
                .post()
                .uri( "/api/v2_1/orders")
                .header(HttpHeaders.AUTHORIZATION,
                        "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(PayUOrderResponse.class);
    }


    private String getAccessToken() {

        PayUAuthResponse response = restClient
                .post()
                .uri("/pl/standard/user/oauth/authorize")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body("grant_type=client_credentials"
                                + "&client_id=" + payUProperties.getClientId()
                                + "&client_secret=" + payUProperties.getClientSecret())
                .retrieve()
                .body(PayUAuthResponse.class);

        if (response == null || response.getAccessToken() == null) {
            throw new IllegalStateException("Unable to obtain PayU access token");
        }

        return response.getAccessToken();
    }

    public void verifyNotificationSignature(String rawBody, String signatureHeader) {
        if (signatureHeader == null || signatureHeader.isBlank()) {
            throw new IllegalArgumentException(
                    "Missing PayU notification signature"
            );
        }

        String incomingSignature = extractSignature(signatureHeader);
        String algorithm = extractValue(signatureHeader, "algorithm");

        if (incomingSignature == null || incomingSignature.isBlank()) {
            throw new IllegalArgumentException(
                    "Missing signature in PayU notification");
        }

        if (!"MD5".equalsIgnoreCase(algorithm)) {
            throw new IllegalArgumentException(
                    "Unsupported PayU signature algorithm: " + algorithm
            );
        }

        String expectedSignature = md5(rawBody +payUProperties.getSecondKey());

        if (!MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.UTF_8),
                incomingSignature.getBytes(StandardCharsets.UTF_8)
        )) {
            throw new IllegalArgumentException(
                    "Invalid PayU notification signature"
            );
        }
    }

    private String extractSignature(String header) {
        return extractValue(header, "signature");
    }

    private String extractValue(String header, String key) {
        for (String part : header.split(";")) {
            String[] pair = part.trim().split("=", 2);

            if (pair.length == 2 && key.equalsIgnoreCase(pair[0].trim())) {
                return pair[1].trim();
            }
        }

        return null;
    }

    private String md5(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");

            byte[] digest = md.digest(
                    value.getBytes(StandardCharsets.UTF_8));

            StringBuilder result = new StringBuilder();

            for (byte b : digest) {
                result.append(String.format("%02x", b));
            }

            return result.toString();

        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 algorithm not available", e);
        }
    }
}
