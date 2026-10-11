package com.pages.config;


import com.stripe.Stripe;
import com.stripe.StripeClient;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StripeConfig {

    @Value("${stripe.api-key}")
    private String stripeApiKey;

    @PostConstruct
    public void initStripeSecret() {
        Stripe.apiKey = this.stripeApiKey;
    }


    @Value("${stripe.api.key}")
    private String apiKey;

    @Bean
    public StripeClient stripeClient() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Stripe initialization failed: Secret API key property is missing.");
        }

        return new StripeClient(apiKey);
    }
}
