
        package com.pages.service;

import com.pages.dto.PayUAuthResponse;
import com.pages.dto.PayUModels;
import com.pages.exception.PayUValidationException;
import com.pages.util.PayUProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;

@Slf4j
@Service
public class PayUAmlService {

    private static final String AUTH_ENDPOINT =
            "/pl/standard/user/oauth/authorize";

    private static final long TOKEN_EXPIRY_BUFFER_SECONDS = 60;

    private final PayUProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    private volatile String cachedToken;
    private volatile Instant tokenExpiry;

    public PayUAmlService(
            PayUProperties properties,
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper) {

        this.properties = properties;
        this.objectMapper = objectMapper;

        String baseUrl = properties.getBaseUrl();

        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException(
                    "PayU base URL must be configured"
            );
        }

        this.restClient = restClientBuilder
                .baseUrl(baseUrl.trim())
                .build();
    }

    /*
     * Handles PayU HTTP errors without logging response bodies,
     * which may contain sensitive seller or banking information.
     */
    private void handleClientError(
            HttpRequest request,
            ClientHttpResponse response) throws IOException {

        String body = new String(
                response.getBody().readAllBytes(),
                StandardCharsets.UTF_8
        );

        String errorCode = "UNKNOWN";
        String description = "PayU request rejected";

        try {
            JsonNode root = objectMapper.readTree(body);

            JsonNode status = root.path("status");

            if (!status.isMissingNode()) {
                errorCode = status.path("codeLiteral")
                        .asString("UNKNOWN");

                description = status.path("statusDesc")
                        .asString("PayU request rejected");
            } else {
                errorCode = root.path("code")
                        .asString("UNKNOWN");

                description = root.path("message")
                        .asString("PayU request rejected");
            }
        } catch (Exception ignored) {
            // Do not expose the raw response body.
        }

        log.error(
                "PayU error: httpStatus={}, endpoint={}, errorCode={}, description={}",
                response.getStatusCode(),
                request.getURI().getPath(),
                errorCode,
                description
        );

        throw new IllegalStateException(
                "PayU API request failed: HTTP "
                        + response.getStatusCode()
                        + ", code=" + errorCode
        );
    }

    /*
     * Obtains and caches a PayU OAuth access token.
     */
    private synchronized String ensureAuthenticated() {

        Instant now = Instant.now();

        if (cachedToken != null
                && tokenExpiry != null
                && now.isBefore(tokenExpiry)) {
            return cachedToken;
        }

        MultiValueMap<String, String> formData =
                new LinkedMultiValueMap<>();

        formData.add("grant_type", "client_credentials");
        formData.add("client_id", properties.getClientId());
        formData.add("client_secret", properties.getClientSecret());

        PayUAuthResponse response = restClient.post()
                .uri(AUTH_ENDPOINT)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(formData)
                .retrieve()
                .onStatus(
                        HttpStatusCode::isError,
                        this::handleClientError
                )
                .body(PayUAuthResponse.class);

        if (response == null
                || response.getAccessToken() == null
                || response.getAccessToken().isBlank()) {

            throw new IllegalStateException(
                    "PayU authentication returned no access token"
            );
        }

        long expiresIn = response.getExpiresIn();

        if (expiresIn <= TOKEN_EXPIRY_BUFFER_SECONDS) {
            throw new IllegalStateException(
                    "PayU returned an invalid token lifetime"
            );
        }

        this.cachedToken = response.getAccessToken();
        this.tokenExpiry = Instant.now().plusSeconds(
                expiresIn - TOKEN_EXPIRY_BUFFER_SECONDS
        );

        return cachedToken;
    }

    private String getAuthHeader() {
        return "Bearer " + ensureAuthenticated();
    }

    /*
     * Checks verification advice for exactly one seller identifier.
     */
    public PayUModels.VerificationAdviceResponse checkVerificationAdvice(
            String companyTaxId,
            String personalIdentificationNumber) {

        boolean hasTaxId =
                companyTaxId != null && !companyTaxId.isBlank();

        boolean hasPersonalId =
                personalIdentificationNumber != null
                        && !personalIdentificationNumber.isBlank();

        if (hasTaxId == hasPersonalId) {
            throw new IllegalArgumentException(
                    "Provide exactly one of companyTaxId or personalIdentificationNumber"
            );
        }

        return restClient.get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder
                            .path("/api/aml-verification/v1/verification-advice");

                    if (hasTaxId) {
                        builder.queryParam("companyTaxId", companyTaxId.trim());
                    } else {
                        builder.queryParam(
                                "personalIdentificationNumber",
                                personalIdentificationNumber.trim()
                        );
                    }

                    return builder.build();
                })
                .header("Authorization", getAuthHeader())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .onStatus(
                        HttpStatusCode::isError,
                        this::handleClientError)
                .body(PayUModels.VerificationAdviceResponse.class);
    }

    /*
     * Initializes a seller verification process.
     */
    public String initializeVerificationWithType(
            String sellerId,
            String initType) {

        if (sellerId == null || sellerId.isBlank()) {
            throw new IllegalArgumentException(
                    "Seller ID is required"
            );
        }

        if (initType == null || initType.isBlank()) {
            throw new IllegalArgumentException(
                    "Verification type is required"
            );
        }

        PayUModels.InitVerificationRequest payload =
                PayUModels.InitVerificationRequest.builder()
                        .sellerId(sellerId.trim())
                        .type(initType.trim())
                        .build();

        PayUModels.InitVerificationResponse response =
                restClient.post()
                        .uri(
                                "/api/aml-verification/v1/verification"
                        )
                        .header("Authorization", getAuthHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(payload)
                        .retrieve()
                        .onStatus(
                                HttpStatusCode::isError,
                                this::handleClientError
                        )
                        .body(
                                PayUModels.InitVerificationResponse.class
                        );

        if (response == null
                || response.verificationId() == null
                || response.verificationId().isBlank()) {

            throw new IllegalStateException(
                    "PayU did not return a verification ID"
            );
        }

        return response.verificationId();
    }

    /*
     * Submits actual private-seller identity data.
     * Does not inject fabricated GUS or registry values.
     */
    public void submitSellerIdentity(
            String verificationId,
            PayUModels.PrivateSellerProfile profile) {

        if (verificationId == null || verificationId.isBlank()) {
            throw new IllegalArgumentException(
                    "Verification ID is required"
            );
        }

        if (profile == null) {
            throw new IllegalArgumentException(
                    "Seller profile is required"
            );
        }

        if (profile.sellerId() == null
                || profile.sellerId().isBlank()
                || profile.email() == null
                || profile.email().isBlank()
                || profile.name() == null
                || profile.name().isBlank()
                || profile.surname() == null
                || profile.surname().isBlank()
                || profile.dateOfBirth() == null
                || profile.dateOfBirth().isBlank()) {

            throw new IllegalArgumentException(
                    "Required seller identity fields are missing"
            );
        }

        if (profile.address() == null) {
            throw new IllegalArgumentException(
                    "Seller address is required"
            );
        }

        PayUModels.AddressPL address = profile.address();

        if (address.country() == null
                || address.country().isBlank()
                || address.street() == null
                || address.street().isBlank()
                || address.zipcode() == null
                || address.zipcode().isBlank()
                || address.city() == null
                || address.city().isBlank()) {

            throw new IllegalArgumentException(
                    "Complete seller address is required"
            );
        }

        PayUModels.PrivateSellerProfile payload =
                PayUModels.PrivateSellerProfile.builder()
                        .verificationId(verificationId)
                        .sellerId(profile.sellerId())
                        .email(profile.email())
                        .name(profile.name())
                        .surname(profile.surname())
                        .personalIdentificationNumber(
                                profile.personalIdentificationNumber())
                        .dateOfBirth(profile.dateOfBirth())
                        .legalForm("PRIVATE_PERSON")
                        .phone(profile.phone())
                        .address(address)
                        .gusCode(profile.gusCode())
                        .registryNumber(profile.registryNumber())
                        .build();

        restClient.post()
                .uri(
                        "/api/aml-verification/v1/dataloading/seller"
                )
                .header("Authorization", getAuthHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .onStatus(
                        HttpStatusCode::isError,
                        this::handleClientError
                )
                .toBodilessEntity();
    }

    /*
     * Associates the seller's payout bank account with verification.
     */
    public void bindPayoutBankAccount(
            String verificationId,
            String bankDataId,
            String iban,
            String transferId) {

        if (verificationId == null || verificationId.isBlank()) {
            throw new IllegalArgumentException(
                    "Verification ID is required"
            );
        }

        if (bankDataId == null || bankDataId.isBlank()) {
            throw new IllegalArgumentException(
                    "Bank data ID is required"
            );
        }

        if (transferId == null || transferId.isBlank()) {
            throw new IllegalArgumentException(
                    "PayU verification transfer ID is required"
            );
        }

        String formattedIban = normalizePolishIban(iban);

        PayUModels.BankAccountRequest payload =
                PayUModels.BankAccountRequest.builder()
                        .bankDataId(bankDataId)
                        .verificationId(verificationId)
                        .accountNumberRequested(formattedIban)
                        .hasDocument("false")
                        .payoutDataVerificationType(
                                "NO_VERIFICATION_NEEDED"
                        )
                        .verificationTransferId(transferId)
                        .foreign("No")
                        .verified("false")
                        .build();

        restClient.post()
                .uri(
                        "/api/aml-verification/v1/"
                                + "dataloading/payouts/bankAccountData"
                )
                .header("Authorization", getAuthHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .onStatus(
                        HttpStatusCode::isError,
                        this::handleClientError
                )
                .toBodilessEntity();
    }

    private String normalizePolishIban(String iban) {

        if (iban == null || iban.isBlank()) {
            throw new IllegalArgumentException(
                    "Bank account number is required"
            );
        }

        String normalized = iban
                .replaceAll("\\s+", "")
                .toUpperCase(Locale.ROOT);

        if (!normalized.startsWith("PL")) {
            normalized = "PL" + normalized;
        }

        if (!normalized.matches("PL\\d{26}")) {
            throw new IllegalArgumentException(
                    "Invalid Polish IBAN format"
            );
        }

        return normalized;
    }

    /*
     * Completes the verification process.
     */
    public void completeVerificationPipeline(String verificationId) {

        if (verificationId == null || verificationId.isBlank()) {
            throw new IllegalArgumentException(
                    "Verification ID is required"
            );
        }

        PayUModels.CompleteRequest payload =
                PayUModels.CompleteRequest.builder()
                        .verificationId(verificationId)
                        .build();

        restClient.post()
                .uri(
                        "/api/aml-verification/v1/verification/complete"
                )
                .header("Authorization", getAuthHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .onStatus(
                        HttpStatusCode::isError,
                        this::handleClientError
                )
                .toBodilessEntity();
    }
}