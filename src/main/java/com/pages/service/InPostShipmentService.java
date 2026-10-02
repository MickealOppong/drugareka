package com.pages.service;


import com.pages.dto.ShipmentApiRequest;
import com.pages.enums.ShipmentStatus;
import com.pages.model.*;
import com.pages.repository.SellerShipmentRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class InPostShipmentService {

    private final RestTemplate restTemplate;
    private final SellerShipmentRepo sellerShipmentRepo;

    @Value("${inPost.api.url}")
    private String inPostApiUrl;

    @Value("${inPost.api.token}")
    private String inPostApiToken;

    @Value("${inPost.organization.id}")
    private String organizationId;

    private final MessageSource messageSource;

    public InPostShipmentService(SellerShipmentRepo sellerShipmentRepo, MessageSource messageSource) {
        this.messageSource = messageSource;
        this.restTemplate = new RestTemplate();
        this.sellerShipmentRepo = sellerShipmentRepo;
    }

    private String getMessage(String code) {
        return messageSource.getMessage(
                code,
                null,
                LocaleContextHolder.getLocale()
        );
    }

    @Transactional
    public void createAutomatedInPostCourierOrder(
            SellerShipment shipment,
            ShipmentApiRequest request
    ) {

        ListingOrderItem orderItem =
                shipment.getListingOrderItem();

        ListingOrder listingOrder =
                orderItem.getListingOrder();

        Map<String, Object> payload = new HashMap<>();

        // ---------------------------------------------------------
        // Basic shipment data
        // ---------------------------------------------------------

        payload.put(
                "service",
                "inpost_courier_standard"
        );

        payload.put(
                "reference",
                "KASOA.PL-" + listingOrder.getOrderNumber()
        );

        payload.put(
                "comments",
                request.getComment() != null
                        ? request.getComment()
                        : ""
        );

        // InPost expects parcels as an array.
        payload.put(
                "parcels",
                List.of(request.getShipment())
        );

        // ---------------------------------------------------------
        // Sender
        // ---------------------------------------------------------

        payload.put(
                "sender",
                request.getSender()
        );

        // ---------------------------------------------------------
        // Receiver
        // ---------------------------------------------------------

        payload.put(
                "receiver",
                request.getReceiver()
        );

        // ---------------------------------------------------------
        // Optional insurance
        // ---------------------------------------------------------

        Map<String, Object> insurance =
                new HashMap<>();

        insurance.put(
                "amount",
                25
        );

        insurance.put(
                "currency",
                "PLN"
        );

        payload.put(
                "insurance",
                insurance
        );

        // ---------------------------------------------------------
        // HTTP request
        // ---------------------------------------------------------

        HttpHeaders headers =
                new HttpHeaders();

        headers.setContentType(
                MediaType.APPLICATION_JSON
        );

        headers.setBearerAuth(
                inPostApiToken
        );

        HttpEntity<Map<String, Object>> requestEntity =
                new HttpEntity<>(
                        payload,
                        headers
                );

        String endpointUrl =
                inPostApiUrl
                        + "/organizations/"
                        + organizationId
                        + "/shipments";

        try {

            log.info("Creating InPost shipment for Kasoa order {}", listingOrder.getOrderNumber());

            log.debug(
                    "InPost shipment payload: {}",
                    payload
            );

            ResponseEntity<Map> response =
                    restTemplate.postForEntity(
                            endpointUrl,
                            requestEntity,
                            Map.class
                    );

            Map<?, ?> responseBody =
                    response.getBody();

            if (responseBody == null) {

                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,getMessage( "api_errors.inPost_creation.response"));
            }

            // -----------------------------------------------------
            // InPost response
            // -----------------------------------------------------

            String trackingNumber =
                    responseBody.get("tracking_number") != null
                            ? String.valueOf(
                            responseBody.get("tracking_number")
                    )
                            : null;

            String inPostShipmentId =
                    responseBody.get("id") != null
                            ? String.valueOf(
                            responseBody.get("id")
                    )
                            : null;

            if (inPostShipmentId == null
                    || inPostShipmentId.isBlank()) {

                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                       getMessage("api_errors.inPost_shipment.response")
                );
            }

            // -----------------------------------------------------
            // Update SellerShipment
            // -----------------------------------------------------

            shipment.setTrackingNumber(
                    trackingNumber
            );

            shipment.setCarrierShipmentId(
                    inPostShipmentId
            );

            shipment.setShipmentStatus(
                    ShipmentStatus.AWAITING_SHIPMENT
            );

            // -----------------------------------------------------
            // Label
            // -----------------------------------------------------

            if (responseBody.get("label")
                    instanceof Map<?, ?> labelMap) {

                Object labelUrl =
                        labelMap.get("url");

                if (labelUrl != null) {

                    shipment.setLabelUrl(
                            String.valueOf(labelUrl)
                    );
                }
            }

            sellerShipmentRepo.save(
                    shipment
            );

            log.info(
                    "InPost shipment created successfully. " +
                            "SellerShipment={}, InPostShipmentId={}, trackingNumber={}",
                    shipment.getId(),
                    inPostShipmentId,
                    trackingNumber
            );

        } catch (HttpStatusCodeException e) {

            log.error(
                    "InPost API error. HTTP={}, response={}",
                    e.getStatusCode(),
                    e.getResponseBodyAsString(),
                    e
            );

            String localizedErrorPrefix = getMessage("api_errors.dpd_creation.failure");
            throw new ResponseStatusException(e.getStatusCode(),localizedErrorPrefix + " " + e.getMessage(), e);

        } catch (Exception e) {

            log.error(
                    "Unexpected error while creating InPost shipment. " +
                            "SellerShipment={}",
                    shipment.getId(),
                    e
            );

            String localizedErrorPrefix = getMessage("api_errors.inPost_shipment.failure");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,localizedErrorPrefix + " " + e.getMessage(), e);
        }
    }
}

