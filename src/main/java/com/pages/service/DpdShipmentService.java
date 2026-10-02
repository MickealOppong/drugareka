package com.pages.service;

import com.pages.dto.ShipmentApiRequest;
import com.pages.dto.ShipmentApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.Base64;

@Slf4j
@Service
public class DpdShipmentService {

    private final RestTemplate restTemplate;

    @Value("${dpd.api.url}")
    private String apiUrl;

    @Value("${dpd.username}")
    private String username;

    @Value("${dpd.password}")
    private String password;

    private final MessageSource messageSource;

    public DpdShipmentService(MessageSource messageSource) {
        this.messageSource = messageSource;
        this.restTemplate =new RestTemplate();
    }

    private String getMessage(String code) {
        return messageSource.getMessage(
                code,
                null,
                LocaleContextHolder.getLocale()
        );
    }

    /**
     * Creates a DPD shipment for a Kasoa order.
     * Recipient information is supplied by the backend and is never
     * exposed to the seller frontend.
     */
    public ShipmentApiResponse createShipment(ShipmentApiRequest request) {

        String credentials = username + ":" + password;

        String encodedCredentials =
                Base64.getEncoder()
                        .encodeToString(credentials.getBytes());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        headers.set(HttpHeaders.AUTHORIZATION, "Basic " + encodedCredentials);

        HttpEntity<ShipmentApiRequest> entity =
                new HttpEntity<>(request, headers);

        ResponseEntity<ShipmentApiResponse> response =
                restTemplate.exchange(
                        apiUrl + "/public/shipment/v1/generatePackagesNumbers",
                        HttpMethod.POST,
                        entity,
                        ShipmentApiResponse.class
                );

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new ResponseStatusException(response.getStatusCode(),getMessage("api_errors.dpd_creation.failure"));
        }

        if (response.getBody() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,getMessage("api_errors.dpd_creation.response"));
        }

        return response.getBody();
    }
}
