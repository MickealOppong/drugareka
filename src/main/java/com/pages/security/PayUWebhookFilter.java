
package com.pages.security;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.HexFormat;

@Slf4j
@Component
public class PayUWebhookFilter implements Filter {


    @Value("${payu.second-key}")
    private String payuSecondKey;

    @Value("${payu.webhook.path}")
    private String webhookPath;

    @Override
    public void doFilter(
            ServletRequest request,
            ServletResponse response,
            FilterChain chain
    ) throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // Use servlet path to avoid context-path mismatches.
        if (!httpRequest.getServletPath().equals(webhookPath)) {
            chain.doFilter(request, response);
            return;
        }

        // Only accept POST requests at the webhook endpoint.
        if (!"POST".equalsIgnoreCase(httpRequest.getMethod())) {
            httpResponse.sendError(
                    HttpServletResponse.SC_METHOD_NOT_ALLOWED,
                    "Only POST requests are supported."
            );
            return;
        }

        String signatureHeader =
                httpRequest.getHeader("OpenPayU-Signature");

        if (signatureHeader == null || signatureHeader.isBlank()) {
            log.warn("PayU webhook rejected: missing signature header.");
            httpResponse.sendError(
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "Missing PayU signature."
            );
            return;
        }

        try {
            // The wrapper must cache the body and provide a fresh
            // InputStream whenever getInputStream() is called.
            CachedBodyHttpServletRequest wrappedRequest =
                    new CachedBodyHttpServletRequest(httpRequest);

            String rawJsonBody = StreamUtils.copyToString(
                    wrappedRequest.getInputStream(),
                    StandardCharsets.UTF_8
            );

            if (!verifyPayUSignature(rawJsonBody, signatureHeader)) {
                log.warn("PayU webhook rejected: invalid signature.");
                httpResponse.sendError(
                        HttpServletResponse.SC_FORBIDDEN,
                        "Invalid PayU signature."
                );
                return;
            }

            log.info("PayU webhook signature verified.");

            // The wrapped request must allow the body to be read again.
            chain.doFilter(wrappedRequest, response);

        } catch (IOException e) {
            log.error("Failed to read PayU webhook request body.", e);
            throw e;
        } catch (RuntimeException e) {
            log.error("Unexpected PayU webhook verification error.", e);
            throw e;
        }
    }

    private boolean verifyPayUSignature(
            String rawJsonBody,
            String signatureHeader
    ) {
        try {
            Map<String, String> signatureMap =
                    parseSignatureHeader(signatureHeader);

            String incomingSignature = signatureMap.get("signature");
            String algorithm = signatureMap.get("algorithm");

            // PayU's documented notification example uses MD5.
            if (incomingSignature == null
                    || !incomingSignature.matches("(?i)[0-9a-f]{32}")
                    || algorithm == null
                    || !"MD5".equalsIgnoreCase(algorithm)
                    || payuSecondKey == null
                    || payuSecondKey.isBlank()) {
                return false;
            }

            String dataToHash = rawJsonBody + payuSecondKey;

            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hashBytes = md.digest(
                    dataToHash.getBytes(StandardCharsets.UTF_8)
            );

            String calculatedSignature =
                    HexFormat.of().formatHex(hashBytes);

            // Compare decoded bytes rather than ordinary strings.
            return MessageDigest.isEqual(
                    calculatedSignature.getBytes(StandardCharsets.US_ASCII),
                    incomingSignature.toLowerCase()
                            .getBytes(StandardCharsets.US_ASCII)
            );

        } catch (NoSuchAlgorithmException e) {
            log.error("MD5 algorithm is unavailable.", e);
            return false;
        } catch (RuntimeException e) {
            log.error("PayU signature verification failed.", e);
            return false;
        }
    }

    private Map<String, String> parseSignatureHeader(String header) {
        Map<String, String> map = new HashMap<>();

        for (String pair : header.split(";")) {
            String[] keyValue = pair.trim().split("=", 2);

            if (keyValue.length == 2) {
                map.put(
                        keyValue[0].trim().toLowerCase(),
                        keyValue[1].trim()
                );
            }
        }

        return map;
    }
}