package com.pages.service;

import com.pages.dto.ResponseDto;
import com.pages.dto.StripeAccountStatusResponse;
import com.pages.model.AppUser;
import com.pages.model.SellerProfile;
import com.pages.repository.AppUserRepo;
import com.pages.repository.GlobalAddressRepo;
import com.pages.repository.SellerProfileRepo;
import com.stripe.model.v2.core.Account;
import com.stripe.param.v2.core.AccountRetrieveParams;
import com.pages.util.GlobalAddress;
import com.stripe.Stripe;
import com.stripe.StripeClient;
import com.stripe.events.V2CoreAccountCreatedEvent;
import com.stripe.events.V2CoreAccountIncludingIdentityUpdatedEventNotification;
import com.stripe.events.V2CoreAccountUpdatedEvent;
import com.stripe.exception.EventDataObjectDeserializationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.model.v2.core.Account;
import com.stripe.model.v2.core.AccountLink;
import com.stripe.model.v2.core.EventNotification;
import com.stripe.param.AccountRetrieveCurrentParams;

import com.stripe.param.v2.core.AccountCreateParams;
import com.stripe.param.v2.core.AccountLinkCreateParams;

import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.stripe.Stripe.apiKey;

@Slf4j
@Service
public class StripeConnectService {


    private final StripeClient client;
    private final SellerProfileRepo sellerProfileRepo;
    private final AppUserRepo appUserRepo;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    public StripeConnectService( StripeClient client, SellerProfileRepo sellerProfileRepo, AppUserRepo appUserRepo) {
        this.client = client;

        this.sellerProfileRepo = sellerProfileRepo;
        this.appUserRepo = appUserRepo;

    }

    /**
     * Creates a Stripe connected account for a private individual seller.
     * Persist the returned account ID against the seller's SellerProfile.
     * Do not create a new Stripe account if the seller already has one.
     */
    public ResponseDto<Object> createConnectedAccount(
            String email,
            String displayName
    ) throws StripeException {

        if (!StringUtils.hasText(email)) {
            throw new IllegalArgumentException("Seller email is required");
        }

        AccountCreateParams params = AccountCreateParams.builder()
                .setContactEmail(email.trim())
                .setDisplayName(
                        StringUtils.hasText(displayName)
                                ? displayName.trim()
                                : email.trim()
                )
                .setIdentity(
                        AccountCreateParams.Identity.builder()
                                .setCountry(AccountCreateParams.Identity.builder().setCountry("PL").build().getCountry())
                                .setEntityType(
                                        AccountCreateParams.Identity.EntityType.INDIVIDUAL
                                )
                                .build()
                )
                .setConfiguration(
                        AccountCreateParams.Configuration.builder()
                                .setRecipient(
                                        AccountCreateParams.Configuration.Recipient.builder()
                                                .setCapabilities(
                                                        AccountCreateParams.Configuration.Recipient.Capabilities.builder()
                                                                .setStripeBalance(
                                                                        AccountCreateParams.Configuration.Recipient.Capabilities.StripeBalance.builder()
                                                                                .setStripeTransfers(
                                                                                        AccountCreateParams.Configuration.Recipient.Capabilities.StripeBalance.StripeTransfers.builder()
                                                                                                .setRequested(true)
                                                                                                .build()
                                                                                )
                                                                                .build()
                                                                )
                                                                .build()
                                                )
                                                .build()
                                )
                                .build()
                )
                .setDefaults(
                        AccountCreateParams.Defaults.builder()
                                .setResponsibilities(
                                        AccountCreateParams.Defaults.Responsibilities.builder()
                                                .setFeesCollector(
                                                        AccountCreateParams.Defaults.Responsibilities.FeesCollector.APPLICATION
                                                )
                                                .setLossesCollector(
                                                        AccountCreateParams.Defaults.Responsibilities.LossesCollector.APPLICATION
                                                )
                                                .build()
                                )
                                .build()
                )
                .setDashboard(AccountCreateParams.Dashboard.EXPRESS)
                .build();

        Account account = client.v2()
                .core()
                .accounts()
                .create(params);

        if (account == null || !StringUtils.hasText(account.getId())) {
            throw new IllegalStateException(
                    "Stripe did not return a connected account ID."
            );
        }

        return ResponseDto.builder()
                .data(account)
                .httpStatus(HttpStatus.OK.value())
                .build();
    }

    /**
     * Generates a fresh, single-use Stripe-hosted onboarding link.
     * The account ID must come from the authenticated user's own
     * SellerProfile, not from an arbitrary client-supplied account ID.
     */
    public String createOnboardingLink(
            String stripeAccountId) throws StripeException {

        if (!StringUtils.hasText(stripeAccountId)) {
            throw new IllegalArgumentException(
                    "Stripe account ID is required"
            );
        }

        String accountId = stripeAccountId.trim();

        AccountLinkCreateParams params =
                AccountLinkCreateParams.builder()
                        .setAccount(accountId)
                        .setUseCase(
                                AccountLinkCreateParams.UseCase.builder()

                                        .setType(
                                                AccountLinkCreateParams.UseCase.Type
                                                        .ACCOUNT_ONBOARDING
                                        )
                                        .setAccountOnboarding(
                                                AccountLinkCreateParams.UseCase
                                                        .AccountOnboarding.builder()
                                                        .addConfiguration(
                                                                AccountLinkCreateParams.UseCase
                                                                        .AccountOnboarding
                                                                        .Configuration.RECIPIENT
                                                        )
                                                        .setRefreshUrl(
                                                                frontendUrl
                                                                        + "/seller/stripe/refresh"
                                                        )
                                                        .setReturnUrl(
                                                                frontendUrl
                                                                        + "/account/me"
                                                        )
                                                        .build()
                                        )
                                        .build()
                        )
                        .build();

        AccountLink accountLink =client.v2()
                .core()
                .accountLinks()
                .create(params);

        return accountLink.getUrl();
    }

    public void handleAccountUpdated(EventNotification notification)  {

        log.info("notif:{}",notification);

    }



    public ResponseDto<Object> handleAccountUpdated(Jwt jwt) {
        try {
            AppUser appUser = getAuthenticatedUser(jwt);

            SellerProfile sellerProfile = sellerProfileRepo
                    .findByUserId(appUser.getId())
                    .orElse(null);

            if (sellerProfile == null
                    || sellerProfile.getStripeAccountId() == null) {
              StripeAccountStatusResponse accountStatusResponse=  StripeAccountStatusResponse.builder()
                        .identityVerified(false)
                        .canReceivePayouts(false)
                        .actionRequired(false)
                        .requirementsCurrentlyDue(List.of())
                        .build();
                return ResponseDto.builder()
                        .data(accountStatusResponse)
                        .httpStatus(HttpStatus.BAD_REQUEST.value())
                        .message("Could not retrieve record")
                        .build();
            }

            AccountRetrieveParams params = AccountRetrieveParams.builder()
                    .addInclude(AccountRetrieveParams.Include.IDENTITY)
                    .addInclude(AccountRetrieveParams.Include.REQUIREMENTS)
                    .addInclude(
                            AccountRetrieveParams.Include.CONFIGURATION__RECIPIENT
                    )
                    .build();

            Account account = client.v2()
                    .core()
                    .accounts()
                    .retrieve(sellerProfile.getStripeAccountId(), params);

            boolean canReceivePayouts =
                    account.getConfiguration() != null
                            && account.getConfiguration().getRecipient() != null
                            && account.getConfiguration().getRecipient()
                            .getCapabilities() != null
                            && account.getConfiguration().getRecipient()
                            .getCapabilities().getStripeBalance() != null
                            && account.getConfiguration().getRecipient()
                            .getCapabilities().getStripeBalance().getPayouts() != null
                            && "active".equals(
                            account.getConfiguration().getRecipient()
                                    .getCapabilities().getStripeBalance()
                                    .getPayouts().getStatus()
                    );



            // Persist payout status independently from identity verification.

            if (canReceivePayouts
                    && !sellerProfile.isIdentityVerified()) {
                sellerProfile.setIdentityVerified(true);
                sellerProfile.setVerifiedAt(Instant.now());
            }

            sellerProfileRepo.save(sellerProfile);

          StripeAccountStatusResponse accountStatusResponse= StripeAccountStatusResponse.builder()
                    .stripeAccountId(sellerProfile.getStripeAccountId())
                    .identityVerified(sellerProfile.isIdentityVerified())
                    .canReceivePayouts(canReceivePayouts)
                    .build();
          return ResponseDto.builder()
                  .httpStatus(HttpStatus.OK.value())
                  .data(accountStatusResponse)
                  .build();

        } catch (StripeException e) {
            log.error("Failed to retrieve Stripe account status", e);
            return ResponseDto.builder()
                    .httpStatus(HttpStatus.OK.value())
                    .data(null)
                    .message(e.getMessage())
                    .httpStatus(HttpStatus.BAD_REQUEST.value())
                    .build();
        }
    }



    private SellerProfile getOrCreateSellerProfile(AppUser user) {
        return sellerProfileRepo.findByUser(user)
                .orElseGet(() -> {
                    SellerProfile profile = new SellerProfile();
                    profile.setUser(user);
                    profile.setIdentityVerified(false);
                    return sellerProfileRepo.save(profile);
                });
    }


    private AppUser getAuthenticatedUser(Jwt jwt) {
        if (jwt == null || !StringUtils.hasText(jwt.getSubject())) {
            throw new IllegalArgumentException(
                    "Authentication is required."
            );
        }

        return appUserRepo.findByUsername(jwt.getSubject())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Authenticated user was not found."
                ));
    }

    private AppUser getAuthenticatedUser(String username) {
        if (!StringUtils.hasText(username)) {
            throw new IllegalArgumentException(
                    "Authentication is required."
            );
        }

        return appUserRepo.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Authenticated user was not found."
                ));
    }

    public ResponseEntity<?> startOnboarding(Jwt jwt) {

        try {
            AppUser user = getAuthenticatedUser(jwt);
            SellerProfile sellerProfile =
                    getOrCreateSellerProfile(user);

            if (!StringUtils.hasText(
                    sellerProfile.getStripeAccountId())) {

                Account account =(Account)
                        createConnectedAccount(
                                user.getUsername(),
                                user.getFirstName()+" "+user.getFirstName()
                        ).getData();

                if (account == null || account.getId() == null) {
                    throw new IllegalStateException(
                            "Stripe account creation returned no account ID."
                    );
                }
                sellerProfile.setStripeAccountId(account.getId());
                sellerProfileRepo.save(sellerProfile);
            }

            String onboardingUrl =
                        createOnboardingLink(
                            sellerProfile.getStripeAccountId()
                    );

            return ResponseEntity.ok(
                    Map.of("url", onboardingUrl)
            );

        } catch (StripeException e) {
            return ResponseEntity.status(
                            HttpStatus.BAD_GATEWAY)
                    .body(Map.of(
                            "error",
                            "Unable to start Stripe onboarding."+e.getMessage()
                    ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }
}