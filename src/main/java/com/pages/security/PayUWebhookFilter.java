package com.pages.security;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class PayUWebhookFilter implements Filter {

    @Value("${payu.second-key}")
    private String payuSecondKey;

    @Value("${payu.webhook.path}")
    private String webhookPath;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        //  Intercept ONLY requests hitting your designated PayU webhook callback URI path
        if (httpRequest.getRequestURI().equals(webhookPath)) {
            log.info("PayU Gateway: Intercepted inbound transactional webhook data stream.");

            // Extract the secure signature validation header
            String signatureHeader = httpRequest.getHeader("OpenPayU-Signature");
            if (signatureHeader == null || signatureHeader.isBlank()) {
                log.error("PayU Fraud Shield: Reverting endpoint. Missing required OpenPayU-Signature header.");
                httpResponse.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing validation checksum signature.");
                return;
            }

            //  Use a Cached Request Wrapper to read the InputStream stream without consuming it
            // This allows your RestControllers to read the JSON payload downstream normally!
            CachedBodyHttpServletRequest wrappedRequest = new CachedBodyHttpServletRequest(httpRequest);
            String rawJsonBody = StreamUtils.copyToString(wrappedRequest.getInputStream(), StandardCharsets.UTF_8);

            // Cryptographically verify the payload against the checksum
            if (!verifyPayUSignature(rawJsonBody, signatureHeader)) {
                log.error("PayU Fraud Shield: ABORTING TRANSACTION! Calculated hash signature mismatch detected.");
                httpResponse.sendError(HttpServletResponse.SC_FORBIDDEN, "Cryptographic signature validation failure.");
                return;
            }

            log.info("PayU Gateway: Cryptographic verification confirmed. Payload is authentic.");
            chain.doFilter(wrappedRequest, response);
            return;
        }

        // Pass standard public traffic through the filter chain untouched
        chain.doFilter(request, response);
    }

    /**
     * Parses the signature headers and calculates the MD5 hash checksum.
     */
    private boolean verifyPayUSignature(String jsonBody, String signatureHeader) {
        try {
            // PayU passes parameters as key=value strands (e.g. sender=total;signature=hash;algorithm=MD5)
            Map<String, String> signatureMap = parseSignatureHeader(signatureHeader);
            String incomingHash = signatureMap.get("signature");

            if (incomingHash == null) return false;

            // Concatenate the raw incoming JSON body string directly with your POS MD5 Second Secret Key
            String dataToHash = jsonBody + payuSecondKey;

            // Compute MD5 footprint
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hashBytes = md.digest(dataToHash.getBytes(StandardCharsets.UTF_8));

            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            String calculatedHash = sb.toString();

            return calculatedHash.equalsIgnoreCase(incomingHash);
        } catch (Exception e) {
            log.error("Security Gateway Exception: Failure during webhook signature parsing loops", e);
            return false;
        }
    }

    private Map<String, String> parseSignatureHeader(String header) {
        Map<String, String> map = new HashMap<>();
        String[] pairs = header.split(";");
        for (String pair : pairs) {
            String[] keyValue = pair.split("=");
            if (keyValue.length == 2) {
                map.put(keyValue[0].trim(), keyValue[1].trim());
            }
        }
        return map;
    }
}
