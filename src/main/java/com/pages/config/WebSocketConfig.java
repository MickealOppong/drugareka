package com.pages.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.List;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                // Safely allows your local server, your current frontend, and any potential backup subdomains
                .setAllowedOriginPatterns("http://localhost:5173","https://pages-api-production-48f8.up.railway.app",
                        "https://www.spotkac.com", "http://www.spotkac.com");
    }


    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Prefixes for routing messages from client to server
        registry.setApplicationDestinationPrefixes("/app");

        // Prefix for topics clients subscribe to for listening
        registry.enableSimpleBroker("/topic");
    }


}
