package com.pages.service;

import com.pages.dto.*;
import com.pages.enums.*;
import com.pages.exception.AccessPermissionException;
import com.pages.exception.EntityNotFoundException;
import com.pages.model.*;
import com.pages.repository.*;
import com.stripe.model.checkout.Session;
import com.stripe.service.climate.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.IntStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderDisputeService {

    private final ListingOrderItemRepo listingOrderItemRepo;
    private final OrderDisputeRepo orderDisputeRepo;
    private final MessageSource messageSource;
    private final AppUserRepo appUserRepo;
    private final SellerShipmentRepo sellerShipmentRepo;
    private final MediaService mediaService;
    private final OrderReturnService orderReturnService;
    private final EmailNotificationService emailNotificationService;
    private final SellerPayoutRepo sellerPayoutRepo;
    private final PaymentService paymentService;
    private final StripePaymentProviderService stripePaymentProviderService;


    // Dynamic bundle translation shortcut helper method
    private String getMessage(String code) {
        return messageSource.getMessage(code, null, LocaleContextHolder.getLocale());
    }
    public int getAccumulatedItemsFromPage(int currentPage,int pageSize,long totalElements){
        // 1. Calculate how many items exist up to the start boundary of the current page
        int itemsBeforeCurrentPage = currentPage * pageSize;

        // 2. Compute the exact remaining items left in the database for this page and beyond
        int remainingItems = (int)totalElements - itemsBeforeCurrentPage;

        // 3.  If remaining items are less than page size,
        // use the exact remainder; otherwise, add a full standard page size block.
        int accumulatedTotal;
        if (remainingItems < pageSize) {
            accumulatedTotal = itemsBeforeCurrentPage + remainingItems; // Caps exactly at TotalElements
        } else {
            accumulatedTotal = itemsBeforeCurrentPage + pageSize;       // Adds a standard full page block
        }
        return accumulatedTotal;
    }
    private ReturnReason getReturnReason(String reason) {
        if (reason == null) return ReturnReason.OTHER;
        String cleanStatus = reason.trim().toUpperCase();
        return switch (cleanStatus) {
            case "DAMAGED_GOODS" -> ReturnReason.DAMAGED_GOODS;
            case "NOT_AS_DESCRIBED" -> ReturnReason.NOT_AS_DESCRIBED;
            case "WRONG_ITEM" -> ReturnReason.WRONG_ITEM;
            default -> ReturnReason.OTHER;
        };
    }


    @Transactional
    public ListComplaintPage allComplaints(Jwt jwt,int page,int size){

        if(jwt!=null){
            Pageable pageable = PageRequest.of(page-1,
                    size,
                    Sort.by(Sort.Direction.DESC, "createdAt")
            );
            Page<DisputeResponse>  disputeResponses =orderDisputeRepo.findAll(pageable)
                    .map(orderDispute -> {

                        List<String> buyerEvidence = mediaService.getDisputeImageList(orderDispute).stream().map(MediaResponse::getImage).toList();

                        //evidence submitted by buyer
                        InventoryItem inventoryItem =  orderDispute.getListingOrderItem().getInventoryItem()!=null?
                                orderDispute.getListingOrderItem().getInventoryItem():null;
                        Long inventoryItemId = inventoryItem!=null?inventoryItem.getId():null;

                        //original listing images
                        List<String> listingImages = mediaService.getImageList(inventoryItemId).stream().map(MediaResponse::getImage).toList();

                        return DisputeResponse.builder()
                                .id(orderDispute.getId())
                                .status(orderDispute.getDisputeStatus())
                                .ticketId(orderDispute.getTicketId())
                                .orderNumber(orderDispute.getOrderNumber())
                                .user(orderDispute.getUser().getUsername())
                                .reason(orderDispute.getReturnReason().name())
                                .sellerComment(orderDispute.getSellerComment())
                                .buyerComment(orderDispute.getBuyerComment())
                                .createdAt(orderDispute.getCreatedAt())
                                .listingImages(listingImages)
                                .images(buyerEvidence)
                                .build();
                    });
            return ListComplaintPage.builder()
                    .complaintResponseList(disputeResponses.getContent())
                    .page(disputeResponses.getNumber())
                    .pageSize(disputeResponses.getSize())
                    .accumulatedItems(getAccumulatedItemsFromPage(disputeResponses.getNumber(),disputeResponses.getSize(), disputeResponses.getTotalElements()))
                    .totalElements(disputeResponses.getTotalElements())
                    .build();
        }
        return ListComplaintPage.builder().build();
    }

    /**
     *  PROCESSES SELLER COMPLAINT DECISION AND RESOLUTION LIFECYCLE
     * Enforces strict transactional boundaries to handle database rollbacks safely on courier tracking faults.
     */
    @Transactional(rollbackFor = Exception.class)
    public ResponseDto<Object> executeSellerUpdate(
            Jwt jwt,
            String orderNumber,
            String decision,
            String reason
    ) {

        if (jwt == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    getMessage("api_errors.order_dispute.login_required")
            );
        }

        // Validate decision before modifying anything
        if (decision.isBlank() ||
                (!"approve".equalsIgnoreCase(decision) &&
                        !"reject".equalsIgnoreCase(decision))) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    getMessage("api_errors.order_dispute.invalid_decision")
            );
        }

        String normalizedDecision = decision.trim().toLowerCase(Locale.ROOT);

        // Lock dispute to prevent concurrent seller decisions
        OrderDispute orderDispute = orderDisputeRepo
                .findByOrderNumberForUpdate(orderNumber)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        getMessage("api_errors.order_dispute.not_found")
                ));

        AppUser appUser = appUserRepo
                .findByUsername(jwt.getSubject())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        getMessage("api_errors.order_dispute.login_required")
                ));

        ListingOrderItem orderItem = orderDispute.getListingOrderItem();

        // Verify seller ownership
        if (!orderItem.getSeller().getUser().equals(appUser)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    getMessage("api_errors.order_dispute.access_denied")
            );
        }

        // Transaction already completed
        if (orderItem.getReceiptConfirmationStatus()
                == ReceiptConfirmationStatus.CONFIRMED) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    getMessage("api_errors.order_dispute.transaction_closed")
            );
        }

        // Prevent duplicate decisions and repeated Stripe operations
        if (orderDispute.getDisputeStatus() !=
                DisputeStatus.AWAITING_SELLER_DECISION) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    getMessage("api_errors.order_dispute.already_processed")
            );
        }

        try {

            orderDispute.setSellerComment(
                    reason == null ? "" : reason.trim()
            );

            AppUser buyer = orderItem.getListingOrder().getBuyer();
            String buyerEmail = buyer.getUsername();

            // ==========================================
            // SELLER REJECTS COMPLAINT
            // ==========================================
            if ("reject".equals(normalizedDecision)) {

                log.info(
                        "Seller rejected complaint for order {}. Escalating to admin.",
                        orderNumber
                );

                orderDispute.setDisputeStatus(
                        DisputeStatus.UNDER_REVIEW
                );

                orderDisputeRepo.save(orderDispute);

                emailNotificationService.sendSellerResponseToBuyerComplaint(
                        buyerEmail,
                        orderNumber,
                        normalizedDecision,
                        reason
                );

                return ResponseDto.builder()
                        .data(null)
                        .httpStatus(HttpStatus.OK.value())
                        .build();
            }

            // ==========================================
            // SELLER APPROVES COMPLAINT
            // ==========================================
            log.info(
                    "Seller approved complaint for order {}",
                    orderNumber
            );

            boolean personalPickup =
                    "personal_pickup".equalsIgnoreCase(
                            orderItem.getShippingMethod()
                    );

            // ==========================================
            // PERSONAL PICKUP - IMMEDIATE REFUND
            // ==========================================
            if (personalPickup) {

                log.info(
                        "Personal pickup complaint approved. Initiating refund for order {}",
                        orderNumber
                );

                orderDispute.setDisputeStatus(
                        DisputeStatus.RETURN_UPHELD
                );

                orderDisputeRepo.save(orderDispute);

                // Buyer never collected the product.
                // No return shipping is required.
                orderReturnService.executeReturnApprovalBySeller(
                        jwt,
                        orderItem.getId()
                );

                return ResponseDto.builder()
                        .data(orderDispute.getId())
                        .httpStatus(HttpStatus.ACCEPTED.value())
                        .build();
            }

            // ==========================================
            // COURIER - SELLER PAYS RETURN SHIPPING
            // ==========================================
            orderDispute.setDisputeStatus(
                    DisputeStatus.AWAITING_RETURN_PAYMENT
            );

            orderDisputeRepo.save(orderDispute);

            Session session =
                    stripePaymentProviderService
                            .createEmbeddedConnectCheckoutSessionShipmentCost(
                                    orderDispute,
                                    LocaleContextHolder
                                            .getLocale()
                                            .getLanguage()
                            );

            String paymentURL = paymentService
                    .createReturnShippingPayment(
                            session,
                            orderDispute
                    )
                    .getData();

            if (paymentURL == null || paymentURL.isBlank()) {
                throw new IllegalStateException(
                        "Return shipping payment URL was not generated"
                );
            }

            emailNotificationService.sendSellerResponseToBuyerComplaint(
                    buyerEmail,
                    orderNumber,
                    normalizedDecision,
                    reason
            );

            return ResponseDto.builder()
                    .data(paymentURL)
                    .httpStatus(HttpStatus.ACCEPTED.value())
                    .build();

        } catch (ResponseStatusException e) {
            throw e;

        } catch (Exception e) {

            log.error(
                    "Failed to process seller complaint decision for order {}",
                    orderNumber,
                    e
            );

            throw new RuntimeException(
                    getMessage("api_errors.order_dispute.processing_failed"),
                    e
            );
        }
    }




    @Transactional(rollbackFor = Exception.class)
    public ResponseDto<Object> executeAdminReview(
            Jwt jwt,
            String orderNumber,
            String decision,
            String reason
    ) {
        try {

            // =====================================================
            // 1. AUTHENTICATION AND VALIDATION
            // =====================================================

            if (jwt == null) {
                throw new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        getMessage("api_errors.order_dispute.login_required")
                );
            }

            List<String> roles = jwt.getClaimAsStringList("ROLE");

            boolean isAdmin = roles != null
                    && roles.contains("ROLE_ADMIN");

            if (!isAdmin) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        getMessage("api_errors.order_cancellation.unauthorized")
                );
            }

            boolean approve = "approve".equalsIgnoreCase(decision);
            boolean reject = "reject".equalsIgnoreCase(decision);
            boolean notJustified =
                    "not_justified".equalsIgnoreCase(decision);

            if (!approve && !reject && !notJustified) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        getMessage("api_errors.order_dispute.invalid_decision")
                );
            }

            // =====================================================
            // 2. LOCK AND VALIDATE DISPUTE
            // =====================================================

            OrderDispute orderDispute = orderDisputeRepo
                    .findByOrderNumberForUpdate(orderNumber)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            getMessage("api_errors.order_dispute.not_found")
                    ));

            ListingOrderItem orderItem =
                    orderDispute.getListingOrderItem();

            if (orderItem == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Missing order item for dispute: " + orderNumber
                );
            }

            if (orderDispute.getDisputeStatus()
                    != DisputeStatus.UNDER_REVIEW) {

                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        getMessage("api_errors.order_dispute.already_processed")
                );
            }

            if (orderItem.getReceiptConfirmationStatus()
                    == ReceiptConfirmationStatus.CONFIRMED) {

                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        getMessage("api_errors.order_dispute.transaction_closed")
                );
            }

            if (reason == null || reason.isBlank()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Administrative justification is required."
                );
            }

            // =====================================================
            // 3. VALIDATE BUYER COMPLAINT REASON
            // =====================================================

            if (orderDispute.getReturnReason() == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Complaint reason is missing."
                );
            }

            String complaintReason =
                    orderDispute.getReturnReason().name();

            switch (complaintReason) {
                case "DAMAGED_GOODS",
                     "WRONG_ITEM",
                     "NOT_AS_DESCRIBED",
                     "OTHER" -> {
                    // Supported complaint reasons.
                }

                default -> throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Unsupported complaint reason: " + complaintReason
                );
            }

            // =====================================================
            // 4. DETERMINE DELIVERY METHOD
            // =====================================================

            String shippingMethod = sellerShipmentRepo
                    .findByListingOrderItemId(orderItem.getId())
                    .map(SellerShipment::getShippingMethod)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Shipment information not found for order: "
                                    + orderNumber
                    ));

            boolean personalPickup =
                    "PERSONAL_PICKUP".equalsIgnoreCase(shippingMethod);

            if (notJustified && !personalPickup) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Unjustified refusal of collection applies "
                                + "only to personal pickup."
                );
            }

            String buyerEmail = orderItem.getListingOrder()
                    .getBuyer()
                    .getUsername();

            String sellerEmail = orderItem.getSeller()
                    .getUser()
                    .getUsername();

            orderDispute.setAdminNotes(reason.trim());

            // =====================================================
            // 5. SERVICE CHARGE BASED ON ADMIN DECISION
            // =====================================================

            // The buyer's selected complaint reason does not
            // determine the service-charge refund.
            //
            // APPROVE:
            //     Refund service charge under the upheld
            //     complaint policy.
            //
            // REJECT:
            //     Do not automatically cancel personal pickup.
            //
            // NOT_JUSTIFIED:
            //     Buyer refused personal collection without
            //     established grounds. Retain service charge
            //     under the applicable fee agreement.

            log.info(
                    "Admin review. Order: {}, Decision: {}, "
                            + "Complaint reason: {}, "
                            + "Personal pickup: {}, "
                            + "Refund service charge: {}",
                    orderNumber,
                    decision,
                    complaintReason,
                    personalPickup,
                    approve
            );

            // =====================================================
            // 6. ADMIN APPROVES COMPLAINT
            // =====================================================

            if (approve) {

                // =================================================
                // 6A. PERSONAL PICKUP
                // =================================================

                if (personalPickup) {

                    orderDispute.setDisputeStatus(
                            DisputeStatus.APPROVED
                    );

                    orderDisputeRepo.save(orderDispute);

                    // Admin upheld the complaint.
                    // Refund item price and service charge.
                    // Existing admin method handles seller
                    // liability according to your refund rules.

                    orderReturnService.executeReturnApprovalByAdmin(
                            jwt,
                            orderItem.getId()
                    );

                    emailNotificationService
                            .sendAdminResponseToBuyerComplaint(
                                    buyerEmail,
                                    orderNumber,
                                    decision,
                                    reason
                            );

                    return ResponseDto.builder()
                            .data(orderDispute.getId())
                            .httpStatus(HttpStatus.ACCEPTED.value())
                            .build();
                }

                // =================================================
                // 6B. COURIER DELIVERY
                // =================================================

                // Seller pays return shipping first.
                // The subsequent refund workflow must use
                // the approved service-charge refund policy.

                orderDispute.setDisputeStatus(
                        DisputeStatus.AWAITING_RETURN_PAYMENT
                );

                orderDisputeRepo.save(orderDispute);

                Session session = stripePaymentProviderService
                        .createEmbeddedConnectCheckoutSessionShipmentCost(
                                orderDispute,
                                LocaleContextHolder.getLocale().getLanguage()
                        );

                if (session == null || session.getId() == null) {
                    throw new IllegalStateException(
                            "Stripe return shipping session creation failed"
                    );
                }

                ResponseDto<String> paymentResponse =
                        paymentService.createReturnShippingPayment(
                                session,
                                orderDispute
                        );

                if (paymentResponse == null
                        || paymentResponse.getData() == null
                        || paymentResponse.getData().isBlank()) {

                    throw new IllegalStateException(
                            "Return shipping payment URL is unavailable"
                    );
                }

                emailNotificationService
                        .sendReturnShippingPaymentRequest(
                                sellerEmail,
                                orderNumber,
                                orderDispute.getTicketId(),
                                paymentResponse.getData()
                        );

                emailNotificationService
                        .sendAdminResponseToBuyerComplaint(
                                buyerEmail,
                                orderNumber,
                                decision,
                                reason
                        );

                return ResponseDto.builder()
                        .data(orderDispute.getId())
                        .httpStatus(HttpStatus.ACCEPTED.value())
                        .build();
            }

            // =====================================================
            // 7. NOT JUSTIFIED — BUYER REFUSED COLLECTION
            // =====================================================

            if (notJustified) {

                // Admin established:
                // 1. The complaint is not substantiated.
                // 2. The seller made the item available.
                // 3. Buyer refused personal collection.
                // 4. The cancellation requirements are met.
                //
                // Existing cancellation method must:
                // - refund item price only
                // - retain service charge
                // - avoid seller service-charge debit
                // - avoid seller payout
                // - leave the item with the seller

                orderDispute.setDisputeStatus(
                        DisputeStatus.REJECTED
                );

                orderDisputeRepo.save(orderDispute);

                orderReturnService.executeBuyerPickupCancellation(
                        jwt,
                        orderItem.getId()
                );

                log.info(
                        "Unjustified buyer refusal of personal collection. "
                                + "Order: {}. Service charge retained.",
                        orderNumber
                );

                emailNotificationService
                        .sendAdminResponseToBuyerComplaint(
                                buyerEmail,
                                orderNumber,
                                decision,
                                reason
                        );

                return ResponseDto.builder()
                        .data(orderDispute.getId())
                        .httpStatus(HttpStatus.OK.value())
                        .build();
            }

            // =====================================================
            // 8. REJECT — COMPLAINT NOT UPHELD
            // =====================================================

            orderDispute.setDisputeStatus(
                    DisputeStatus.REJECTED
            );

            orderDisputeRepo.save(orderDispute);

            if (personalPickup) {

                // Rejected complaint does not automatically
                // establish buyer refusal of collection.
                //
                // The buyer may still complete collection.
                // Do not cancel, refund or approve payout here.

                log.info(
                        "Personal pickup complaint rejected. "
                                + "Collection remains unresolved. Order: {}",
                        orderNumber
                );

            } else {

                // Courier delivered the item.
                // Complaint rejected.
                // Confirm receipt and approve seller payout.

                orderItem.setReceiptConfirmationStatus(
                        ReceiptConfirmationStatus.CONFIRMED
                );

                listingOrderItemRepo.save(orderItem);

                sellerPayoutRepo
                        .findByListingOrderItemIdAndPayoutStatusForUpdate(
                                orderItem.getId(),
                                PayoutStatus.CREATED
                        )
                        .ifPresent(payout -> {

                            payout.setStatus(
                                    PayoutStatus.APPROVED
                            );

                            sellerPayoutRepo.save(payout);
                        });
            }

            // =====================================================
            // 9. NOTIFY BUYER
            // =====================================================

            emailNotificationService
                    .sendAdminResponseToBuyerComplaint(
                            buyerEmail,
                            orderNumber,
                            decision,
                            reason
                    );

            return ResponseDto.builder()
                    .data(orderDispute.getId())
                    .httpStatus(HttpStatus.OK.value())
                    .build();

        } catch (ResponseStatusException ex) {

            throw ex;

        } catch (Exception ex) {

            log.error(
                    "Admin dispute review failed for order {}",
                    orderNumber,
                    ex
            );

            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    getMessage("api_errors.order_dispute.processing_failed"),
                    ex
            );
        }
    }



    @Transactional
    public void sendPaymentLinkToSellerForReturnShipment() {

        List<OrderDispute> disputes =
                orderDisputeRepo.findAllByDisputeStatus(DisputeStatus.AWAITING_RETURN_PAYMENT);

        log.info(
                "Found {} disputes awaiting return-shipping payment",
                disputes.size()
        );

        for (OrderDispute dispute : disputes) {

            try {
                Long disputeId = dispute.getId();

                // The payment record must already exist.
                // It is created when the seller or admin approves the return.
                String sessionId = paymentService.getSession(disputeId);

                if (sessionId == null || sessionId.isBlank()) {
                    log.error(
                            "Dispute {} is awaiting return payment but has no payment session",
                            disputeId
                    );
                    continue;
                }

                // Retrieve the existing Stripe Checkout Session.
                Session session = Session.retrieve(sessionId);

                if ("expired".equalsIgnoreCase(session.getStatus())) {

                    // Replace only the expired Checkout Session.
                    session = stripePaymentProviderService
                            .createEmbeddedConnectCheckoutSessionShipmentCost(
                                    dispute,
                                    "pl"
                            );

                    if (session == null
                            || session.getId() == null
                            || session.getUrl() == null
                            || session.getUrl().isBlank()) {

                        log.error(
                                "Failed to create replacement Checkout Session for dispute {}",
                                disputeId
                        );
                        continue;
                    }

                    // Update the existing Payment record.
                    // Do not create another payment obligation.
                    paymentService.updateSession(
                            disputeId,
                            session.getId()
                    );

                    log.info(
                            "Replaced expired Checkout Session for dispute {} with {}",
                            disputeId,
                            session.getId()
                    );

                } else if (!"open".equalsIgnoreCase(session.getStatus())
                        || !"unpaid".equalsIgnoreCase(session.getPaymentStatus())) {

                    log.info(
                            "Skipping dispute {}. Session status: {}, Payment status: {}",
                            disputeId,
                            session.getStatus(),
                            session.getPaymentStatus()
                    );
                    continue;
                }

                String paymentUrl = session.getUrl();

                if (paymentUrl == null || paymentUrl.isBlank()) {
                    log.warn(
                            "Checkout Session has no payment URL. Dispute: {}",
                            disputeId
                    );
                    continue;
                }

                ListingOrderItem orderItem =
                        dispute.getListingOrderItem();

                String sellerEmail =
                        orderItem.getSeller()
                                .getUser()
                                .getUsername();

                String sellerName =
                        orderItem.getSeller()
                                .getFullLegalName();

                emailNotificationService.sendPaymentLink(
                        sellerEmail,
                        sellerName,
                        paymentUrl
                );

                log.info(
                        "Return-shipping payment reminder sent. Dispute: {}, Seller: {}",
                        disputeId,
                        sellerEmail
                );

            } catch (Exception ex) {

                log.error(
                        "Failed to process return-shipping payment reminder. Dispute: {}",
                        dispute.getId(),
                        ex
                );
            }
        }
    }







    @Transactional
    public DisputeResponse getOrderDispute(Jwt jwt,String orderNumber){

        if(jwt!=null){

            return orderDisputeRepo.findByOrderNumber(orderNumber)
                    .map(orderDispute -> {

                        List<String> buyerEvidence = mediaService.getDisputeImageList(orderDispute).stream().map(MediaResponse::getImage).toList();

                        //evidence submitted by buyer
                        InventoryItem inventoryItem =  orderDispute.getListingOrderItem().getInventoryItem()!=null?
                                orderDispute.getListingOrderItem().getInventoryItem():null;
                        Long inventoryItemId = inventoryItem!=null?inventoryItem.getId():null;

                        //original listing images
                        List<String> listingImages = mediaService.getImageList(inventoryItemId).stream().map(MediaResponse::getImage).toList();

                        return DisputeResponse.builder()
                                .id(orderDispute.getId())
                                .status(orderDispute.getDisputeStatus())
                                .ticketId(orderDispute.getTicketId())
                                .orderNumber(orderDispute.getOrderNumber())
                                .user(orderDispute.getUser().getUsername())
                                .reason(orderDispute.getReturnReason().name())
                                .sellerComment(orderDispute.getSellerComment())
                                .shippingMethod(orderDispute.getListingOrderItem().getShippingMethod())
                                .buyerComment(orderDispute.getBuyerComment())
                                .createdAt(orderDispute.getCreatedAt())
                                .status(orderDispute.getDisputeStatus())
                                .listingImages(listingImages)
                                .images(buyerEvidence)
                                .build();
                    }).orElse(null);

        }
        return DisputeResponse.builder().build();
    }

    /**
     *  PROTECTED RETURN & ANTI-WITHDRAWAL PIPELINE
     * Prevents buyers from canceling paid orders mid-transit to protect sellers.
     * Restricts the return flow strictly to items that have been verified as DELIVERED.
     */
    @Transactional
    public Boolean logDispute(Jwt jwt, OrderReturnRequest request) {


        // 1. Authenticate buyer
        if (jwt == null) {
            throw new AccessPermissionException(
                    getMessage("api_errors.order_return.access_denied")
            );
        }



        AppUser appUser = appUserRepo.findByUsername(jwt.getSubject())
                .orElseThrow(() -> new UsernameNotFoundException(
                        getMessage("api_errors.order_return.user_not_found")
                ));

        if (request == null || request.getOrderItemId() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    getMessage("api_errors.order_return.item_not_found")
            );
        }

        // 2. Lock order item to prevent concurrent dispute submissions
        ListingOrderItem orderItem = listingOrderItemRepo
                .findByIdForUpdate(request.getOrderItemId())
                .orElseThrow(() -> new EntityNotFoundException(
                        getMessage("api_errors.order_return.item_not_found")
                ));

        // 2a. Prevent duplicate disputes
        boolean existingDispute = orderDisputeRepo
                .findByListingOrderItem(orderItem)
                .isPresent();

        if (existingDispute) {
            throw new DuplicateKeyException(
                    getMessage("api_errors.order_return.duplicate_record")
            );
        }

        // 3. Verify buyer ownership
        ListingOrder parentOrder = orderItem.getListingOrder();

        if (parentOrder == null
                || parentOrder.getBuyer() == null
                || !parentOrder.getBuyer().getId().equals(appUser.getId())) {

            throw new AccessPermissionException(
                    getMessage("api_errors.order_return.not_your_order")
            );
        }

        // 4. Prevent disputes after receipt has been confirmed
        if (orderItem.getReceiptConfirmationStatus()
                == ReceiptConfirmationStatus.CONFIRMED) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    getMessage("api_errors.order_dispute.transaction_closed")
            );
        }

        // 5. Only allow disputes for paid order items
        if (orderItem.getOrderItemStatus() != OrderItemStatus.PAID) {

            log.warn(
                    "Dispute blocked: Buyer [{}] attempted to dispute order item [{}] with status [{}].",
                    appUser.getId(),
                    orderItem.getId(),
                    orderItem.getOrderItemStatus()
            );

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    getMessage("api_errors.order_return.withdrawal_blocked")
            );
        }

        // 6. Find associated shipment / collection record
        SellerShipment sellerShipment = sellerShipmentRepo
                .findByListingOrderItemId(request.getOrderItemId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        getMessage("api_errors.order_return.access_denied")
                ));

        ShipmentStatus shipmentStatus = sellerShipment.getShipmentStatus();

        // Allow complaints only after delivery or rejected personal collection
        if (shipmentStatus != ShipmentStatus.DELIVERED && shipmentStatus != ShipmentStatus.REJECTED  && shipmentStatus != ShipmentStatus.RETURNED) {

            log.warn(
                    "Dispute blocked: Buyer [{}] attempted to dispute order item [{}] with shipment status [{}].",
                    appUser.getId(),
                    orderItem.getId(),
                    shipmentStatus
            );

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    getMessage("api_errors.order_return.withdrawal_blocked")
            );
        }

        // 7. Check complaint deadline
        // Assumes deliveredAt records the relevant event time for both statuses.
        // 7. Apply the 24-hour complaint deadline only to non-personal collections
        boolean isPersonalCollection =
                sellerShipment.getShippingMethod() != null
                        && sellerShipment.getShippingMethod()
                        .equalsIgnoreCase("personal_pickup");

        if (!isPersonalCollection) {

            Instant deliveredAt = sellerShipment.getDeliveredAt();

            if (deliveredAt == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        getMessage("api_errors.order_return.withdrawal_blocked")
                );
            }

            Instant deadline = deliveredAt.plus(24, ChronoUnit.HOURS);

            if (Instant.now().isAfter(deadline)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        getMessage("api_errors.order_return.deadline_expired")
                );
            }
        }



        // 9. Generate dispute reference
        String ticketNumber = "TK-" +
                UUID.randomUUID()
                        .toString()
                        .substring(0, 8)
                        .toUpperCase(Locale.ROOT);

        // 10. Create dispute record
        OrderDispute orderReturn = OrderDispute.builder()
                .buyerComment(request.getBuyerComment())
                .listingOrderItem(orderItem)
                .returnReason(getReturnReason(request.getReturnReason()))
                .disputeStatus(DisputeStatus.AWAITING_SELLER_DECISION)
                .orderNumber(parentOrder.getOrderNumber())
                .user(appUser)
                .ticketId(ticketNumber)
                .build();

        OrderDispute savedReturn = orderDisputeRepo.save(orderReturn);

        // 11. Upload evidence only when provided
        if (request.getImages() != null && request.getImages().length > 0) {

            Integer[] sortOrder = IntStream
                    .range(0, request.getImages().length)
                    .boxed()
                    .toArray(Integer[]::new);

            mediaService.uploadReturnImage(
                    request.getImages(),
                    sortOrder,
                    savedReturn
            );
        }

        // 12. Hold receipt confirmation while dispute is being reviewed
        orderItem.setReceiptConfirmationStatus(
                ReceiptConfirmationStatus.PENDING
        );

        listingOrderItemRepo.save(orderItem);


        // 13. Notify seller
        String email = orderItem.getSeller()
                .getUser()
                .getUsername();

        String orderNumber = parentOrder.getOrderNumber();
        String reason = savedReturn.getReturnReason().name();

        emailNotificationService.notifySellerOfBuyerReturnComplaint(
                email,
                orderNumber,
                reason
        );

        log.info(
                "Dispute [{}] opened by buyer [{}] for order [{}], order item [{}]. Payout confirmation placed on hold.",
                ticketNumber,
                appUser.getId(),
                orderNumber,
                orderItem.getId()
        );

        return true;
    }
}
