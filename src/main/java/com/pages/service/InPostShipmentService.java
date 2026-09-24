package com.pages.service;

import com.pages.dto.InPostRequest;
import com.pages.dto.InPostShipmentResponse;
import com.pages.dto.InPostTokenResponse;
import com.pages.dto.ResponseDto;
import com.pages.model.ListingOrderItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InPostShipmentService {

/*
    private final RestClient restClient;
    private final InPostAuthService authService;

    @Value("${inpost.base-url}")
    private String baseUrl;

    @Value("${inpost.organization-id}")
    private String organizationId;

    public InPostShipmentResponse createShipment(InPostRequest request) {

        String token = authService.getAccessToken();

        return restClient
                .post()
                .uri(baseUrl + "/shipping/v2/organizations/"
                                + organizationId
                                + "/shipments")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header("X-Request-Id", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(InPostShipmentResponse.class);
    }

 */
}
