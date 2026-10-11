package com.pages.util;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "payu.config")
public class PayUProperties {
    @Value("${payu.base-url}")
    private String baseUrl;

    @Value("${payu.pos-id}")
    private String posId;

    @Value("${payu.client-id}")
    private String clientId;

    @Value("${payu.client-secret}")
    private String clientSecret;

    @Value("${payu.notify-url}")
    private String notifyUrl;

    @Value("${payu.continue-url}")
    private String continueUrl;

    @Value("${payu.second-key}")
    private String secondKey;
}
