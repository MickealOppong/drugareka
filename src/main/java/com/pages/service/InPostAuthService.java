package com.pages.service;

import com.pages.dto.InPostTokenResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@RequiredArgsConstructor
public class InPostAuthService {

    /*
    private final RestClient restClient;

    @Value("${inpost.client-id}")
    private String clientId;

    @Value("${inpost.client-secret}")
    private String clientSecret;

    public String getAccessToken() {

        InPostTokenResponse response =
                restClient
                        .post()
                        .uri("https://api.inpost-group.com/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .body(
                                "grant_type=client_credentials"
                                        + "&client_id=" + clientId
                                        + "&client_secret=" + clientSecret
                                        + "&scope=openid%20api:shipments:write"
                        )
                        .retrieve()
                        .body(InPostTokenResponse.class);

        if (response == null || response.getAccessToken() == null) {
            throw new IllegalStateException(
                    "Unable to obtain InPost access token"
            );
        }

        return response.getAccessToken();
    }

     */
}