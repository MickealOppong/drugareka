package com.pages.service;

import com.nimbusds.jose.shaded.gson.JsonParseException;
import com.pages.dto.ResponseDto;
import com.pages.dto.StripeAccountStatusResponse;
import com.pages.enums.InventoryStatus;
import com.pages.enums.OrderItemStatus;
import com.pages.enums.OrderStatus;
import com.pages.enums.PaymentStatus;
import com.pages.model.*;
import com.pages.repository.*;
import com.stripe.exception.EventDataObjectDeserializationException;
import com.stripe.model.Event;
import com.stripe.model.LoginLink;
import com.stripe.model.StripeObject;
import com.stripe.model.v2.core.Account;
import com.stripe.param.v2.core.AccountRetrieveParams;
import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.v2.core.AccountLink;
import com.stripe.model.v2.core.EventNotification;

import com.stripe.param.v2.core.AccountCreateParams;
import com.stripe.param.v2.core.AccountLinkCreateParams;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class StripeConnectService {


    private final StripeClient client;
    private final SellerProfileRepo sellerProfileRepo;
    private final AppUserRepo appUserRepo;
    private final ObjectMapper objectMapper;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    public StripeConnectService(StripeClient client, SellerProfileRepo sellerProfileRepo, AppUserRepo appUserRepo, ObjectMapper objectMapper) {
        this.client = client;

        this.sellerProfileRepo = sellerProfileRepo;
        this.appUserRepo = appUserRepo;

        this.objectMapper = objectMapper;
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



    public String createStripeDashboardLink(String stripeAccountId)
            throws StripeException {

        if (stripeAccountId == null || stripeAccountId.isBlank()) {
            throw new IllegalStateException(
                    "Seller does not have a Stripe connected account"
            );
        }

        LoginLink loginLink = LoginLink.createOnAccount(stripeAccountId);
        return loginLink.getUrl();
    }

    @Transactional
    public void handleAccountUpdated(Event event) throws EventDataObjectDeserializationException {

       com.stripe.model.Account account = (com.stripe.model.Account) event.getDataObjectDeserializer().deserializeUnsafe();

        if (account==null) {
            log.error(
                    "Unable to deserialize account.updated event. eventId={}",
                    event.getId()
            );
            return;
        }

        String stripeAccountId = account.getId();

        if (!StringUtils.hasText(stripeAccountId)) {
            log.error(
                    "Stripe account ID missing. eventId={}",
                    event.getId()
            );
            return;
        }

        SellerProfile seller = sellerProfileRepo
                .findByStripeAccountId(stripeAccountId)
                .orElse(null);

        if (seller == null) {
            log.warn(
                    "No seller profile matches Stripe account. eventId={}, accountId={}",
                    event.getId(),
                    stripeAccountId
            );
            return;
        }

        // Core account state
        seller.setStripeOnboardingComplete(
                Boolean.TRUE.equals(account.getDetailsSubmitted())
        );

        seller.setStripeChargesEnabled(
                Boolean.TRUE.equals(account.getChargesEnabled())
        );

        seller.setStripePayoutsEnabled(
                Boolean.TRUE.equals(account.getPayoutsEnabled())
        );

        // Legacy account.updated capability state
        String transfersStatus = null;

        if (account.getCapabilities() != null) {
            transfersStatus = account.getCapabilities().getTransfers();
        }

        seller.setStripeTransfersStatus(transfersStatus);
        seller.setStripeTransfersEnabled(
                "active".equalsIgnoreCase(transfersStatus)
        );

        // Outstanding requirements and verification errors
        com.stripe.model.Account.Requirements requirements = account.getRequirements();

        if (requirements != null) {
            seller.setStripeCurrentlyDue(
                    toJson(requirements.getCurrentlyDue())
            );

            seller.setStripeEventuallyDue(
                    toJson(requirements.getEventuallyDue())
            );

            seller.setStripePastDue(
                    toJson(requirements.getPastDue())
            );

            seller.setStripeDisabledReason(
                    requirements.getDisabledReason()
            );

            List<com.stripe.model.Account.Requirements.Errors> errors =
                    requirements.getErrors() == null
                            ? List.of()
                            : requirements.getErrors();

            seller.setStripeVerificationErrors(
                    toJson(
                            errors.stream()
                                    .map(error -> Map.of(
                                            "code", safe(error.getCode()),
                                            "reason", safe(error.getReason()),
                                            "requirement",
                                            safe(error.getRequirement())
                                    ))
                                    .toList()
                    )
            );
        } else {
            seller.setStripeCurrentlyDue("[]");
            seller.setStripeEventuallyDue("[]");
            seller.setStripePastDue("[]");
            seller.setStripeVerificationErrors("[]");
            seller.setStripeDisabledReason(null);
        }

        seller.setStripeAccountUpdatedAt(Instant.now());

        sellerProfileRepo.save(seller);

        log.info(
                "Stripe account synchronized. eventId={}, accountId={}, " +
                        "onboardingComplete={}, transfersStatus={}, payoutsEnabled={}, " +
                        "chargesEnabled={}, disabledReason={}",
                event.getId(),
                stripeAccountId,
                seller.isStripeOnboardingComplete(),
                transfersStatus,
                seller.isStripePayoutsEnabled(),
                seller.isStripeChargesEnabled(),
                seller.getStripeDisabledReason()
        );
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(
                    value == null ? List.of() : value
            );
        } catch (JsonParseException ex) {
            throw new IllegalStateException(
                    "Failed to serialize Stripe account requirements",
                    ex
            );
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
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

    @Slf4j
    @Component
    @RequiredArgsConstructor
    public static class OrderFinalizerWorker {

        private final PaymentRepo paymentRepo;
        private final ListingOrderRepo listingOrderRepo;
        private final InventoryItemRepo inventoryItemRepo;
        private final ListingOrderItemRepo listingOrderItemRepo;
        private final ServiceChargeService serviceChargeService;
        private final SellerPayoutService sellerPayoutService;
        private final SellerProfileService sellerProfileService;
        private final CartService cartService;

        @org.springframework.transaction.annotation.Transactional
        public ListingOrder executeFinalization(String sessionId, Long orderId, String paymentIntentId, String chargeId) {
            // 1. Lock and validate Payment record
            Payment payment = paymentRepo
                    .findByProviderSessionIdForUpdate(sessionId)
                    .orElseThrow(() -> new IllegalStateException("Payment not found for session: " + sessionId));

            if (payment.getStatus() == PaymentStatus.PAID) {
                log.info("Payment already finalized for session {}", sessionId);
                return null; // Idempotent shortcut exit
            }

            // 2. Lock and validate Order record
            ListingOrder order = listingOrderRepo
                    .findByIdForUpdate(orderId)
                    .orElseThrow(() -> new IllegalStateException("Order not found: " + orderId));

            if (order.getOrderStatus() == OrderStatus.PAID) {
                log.info("Order {} is already paid", orderId);
                return null; // Idempotent shortcut exit
            }

            Instant paidAt = Instant.now();

            // 3. Update core transaction records
            payment.setStatus(PaymentStatus.PAID);
            payment.setPaidAt(paidAt);
            payment.setStripePaymentIntentId(paymentIntentId);
            payment.setStripeChargeId(chargeId);

            order.setOrderStatus(OrderStatus.PAID);
            order.setPaidAt(paidAt);

            // 4. Validate and commit inventory state shifts
            for (ListingOrderItem item : order.getItems()) {
                InventoryItem inventory = item.getInventoryItem();
                if (inventory.getStatus() == InventoryStatus.SOLD) {
                    throw new IllegalStateException("Inventory conflict: item " + item.getId() + " is already sold");
                }
                inventory.setStatus(InventoryStatus.SOLD);
                inventory.setReservedUntil(null);
                inventory.setReservedBy(null);
                inventoryItemRepo.save(inventory);

                item.setOrderItemStatus(OrderItemStatus.PAID);
                listingOrderItemRepo.save(item);
            }

            paymentRepo.save(payment);
            ListingOrder savedOrder = listingOrderRepo.save(order);

            // 5. Downstream internal structural workflows
            serviceChargeService.createCharge(savedOrder);
            sellerPayoutService.createPayouts(savedOrder);
            sellerProfileService.updateSellerTotalSales(savedOrder.getItems());
            cartService.deleteCartById(savedOrder.getBuyer().getId());

            return savedOrder;
        }

        @org.springframework.transaction.annotation.Transactional
        public ListingOrder executeDelayedPaymentSuccessFulfillment(Long orderId, String chargeId) {
            ListingOrder order = listingOrderRepo.findByIdForUpdate(orderId)
                    .orElseThrow(() -> new IllegalStateException("Order missing for execution: " + orderId));

            // Verify inventory availability before overriding state changes
            for (ListingOrderItem item : order.getItems()) {
                if (item.getInventoryItem().getStatus() == InventoryStatus.SOLD) {
                    return null; // Conflict found; inventory was lost while out-of-time
                }
            }

            order.setOrderStatus(OrderStatus.PAID);
            order.setPaidAt(Instant.now());

            for (ListingOrderItem item : order.getItems()) {
                InventoryItem inventory = item.getInventoryItem();
                inventory.setStatus(InventoryStatus.SOLD);
                inventory.setReservedBy(null);
                inventory.setReservedUntil(null);
                inventoryItemRepo.save(inventory);
            }

            ListingOrder savedOrder = listingOrderRepo.save(order);

            serviceChargeService.createCharge(savedOrder);
            sellerPayoutService.createPayouts(savedOrder);
            sellerProfileService.updateSellerTotalSales(savedOrder.getItems());
            cartService.deleteCartById(savedOrder.getBuyer().getId());

            return savedOrder;
        }

        @Transactional
        public boolean executeCheckoutCancellation(String sessionId, Long orderId) {
            Payment payment = paymentRepo.findByProviderSessionIdForUpdate(sessionId).orElse(null);
            if (payment != null && payment.getStatus() == PaymentStatus.PAID) {
                return false;
            }

            ListingOrder order = listingOrderRepo.findByIdForUpdate(orderId)
                    .orElseThrow(() -> new IllegalStateException("Order not found for eviction: " + orderId));

            if (order.getOrderStatus() == OrderStatus.PAID || order.getOrderStatus() == OrderStatus.CANCELLED) {
                return false;
            }

            if (payment != null) {
                payment.setStatus(PaymentStatus.FAILED);
                paymentRepo.save(payment);
            }

            order.setOrderStatus(OrderStatus.CANCELLED);

            for (ListingOrderItem item : order.getItems()) {
                InventoryItem inventory = item.getInventoryItem();
                if (inventory.getStatus() == InventoryStatus.RESERVED) {
                    inventory.setStatus(InventoryStatus.AVAILABLE);
                    inventory.setReservedUntil(null);
                    inventory.setReservedBy(null);
                    inventoryItemRepo.save(inventory);
                }
                item.setOrderItemStatus(OrderItemStatus.CANCELLED);
                listingOrderItemRepo.save(item);
            }

            listingOrderRepo.save(order);
            return true;
        }
    }
}