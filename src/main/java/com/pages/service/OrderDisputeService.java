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
    @Transactional
    public ResponseDto<Object> executeSellerUpdate(Jwt jwt, String orderNumber, String decision, String reason) {
        if (jwt == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, getMessage("api_errors.order_dispute.login_required"));
        }

        // Isolated row-level pessimistic write lock prevents simultaneous admin race conditions
        OrderDispute orderDispute = orderDisputeRepo.findByOrderNumberForUpdate(orderNumber)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, getMessage("api_errors.order_dispute.not_found")));

        AppUser appUser = appUserRepo.findByUsername(jwt.getSubject()).orElse(null);

        if(!orderDispute.getListingOrderItem().getSeller().getUser().equals(appUser)){
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, getMessage("api_errors.order_dispute.access_denied"));
        }

        ListingOrderItem orderItem = orderDispute.getListingOrderItem();
        if(orderItem.getReceiptConfirmationStatus()==ReceiptConfirmationStatus.CONFIRMED){
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, getMessage("api_errors.order_dispute.transaction_closed"));
        }

        // Safeguard Guard: Stop mutations if the case has already progressed beyond actionable boundaries
        if (orderDispute.getDisputeStatus() == DisputeStatus.RESOLVED ||
                orderDispute.getDisputeStatus() == DisputeStatus.APPROVED)
        {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, getMessage("api_errors.order_dispute.already_processed"));
        }

        try {
            orderDispute.setSellerComment(reason != null ? reason.trim() : "");

            if ("approve".equalsIgnoreCase(decision)) {
                log.info("Seller approved complaint return for order: {}. Upgrading tracking status rules.", orderNumber);

                //refund money after approval for personal pickups
                if(orderDispute.getListingOrderItem().getShippingMethod().equalsIgnoreCase("personal_pickup")){
                    orderDispute.setDisputeStatus(DisputeStatus.RETURN_UPHELD);
                    OrderDispute updatedItem= orderDisputeRepo.save(orderDispute);

                    orderReturnService.executeReturnApprovalBySeller(jwt,orderDispute.getListingOrderItem().getId());
                   return ResponseDto.builder()
                            .data(updatedItem.getId())
                            .httpStatus(HttpStatus.ACCEPTED.value())
                            .build();
                }

                orderDispute.setDisputeStatus(DisputeStatus.AWAITING_RETURN_PAYMENT);
                OrderDispute updatedItem= orderDisputeRepo.save(orderDispute);

                AppUser buyer = orderDispute.getListingOrderItem().getListingOrder().getBuyer();
                String email = buyer.getUsername();

                emailNotificationService.sendSellerResponseToBuyerComplaint(email,orderNumber,decision, reason);
                return ResponseDto.builder()
                        .data(updatedItem.getId())
                        .httpStatus(HttpStatus.ACCEPTED.value())
                        .build();

            }
            else if ("reject".equalsIgnoreCase(decision)) {
                log.info("Seller rejected complaint counter-offer for order: {}. Escalating to platform moderator view.", orderNumber);
                // Escalates the case to platform administrators for formal mediation review
                orderDispute.setDisputeStatus(DisputeStatus.UNDER_REVIEW);
                orderDisputeRepo.save(orderDispute);

                AppUser buyer = orderDispute.getListingOrderItem().getListingOrder().getBuyer();
                String email = buyer.getUsername();

                emailNotificationService.sendSellerResponseToBuyerComplaint(email,orderNumber,decision, reason);

                return ResponseDto.builder()
                        .data(null)
                        .httpStatus(HttpStatus.OK.value())
                        .build();
            }
            else {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, getMessage("api_errors.order_dispute.invalid_decision"));
            }


        } catch (ResponseStatusException e) {
            // Rethrow specific API exceptions to pass your customized error message headers straight down to your React alerts
            throw e;
        } catch (Exception e) {

            // to ensure Spring's transaction interceptor executes a 100% clean database rollback!
            log.error("Fatal failure executing seller transaction update for dispute on order {}: {}", orderNumber, e.getMessage(), e);
            throw new RuntimeException(getMessage("api_errors.order_dispute.processing_failed"), e);
        }
    }



    /**
     *  PROCESSES BUYER COMPLAINT DECISION AND RESOLUTION LIFECYCLE
     * Enforces strict transactional boundaries to handle database rollbacks safely on courier tracking faults.
     */
    @Transactional
    public ResponseDto<Object> executeAdminReview(Jwt jwt, String orderNumber, String decision, String reason) {
        if (jwt == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, getMessage("api_errors.order_dispute.login_required"));
        }

        // Isolated row-level pessimistic write lock prevents simultaneous admin race conditions
        OrderDispute orderDispute = orderDisputeRepo.findByOrderNumberForUpdate(orderNumber)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, getMessage("api_errors.order_dispute.not_found")));

        ListingOrderItem orderItem = orderDispute.getListingOrderItem();
        if(orderItem.getReceiptConfirmationStatus()==ReceiptConfirmationStatus.CONFIRMED){
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, getMessage("api_errors.order_dispute.transaction_closed"));
        }

        // Safeguard Guard: Stop mutations if the case has already progressed beyond actionable boundaries
        if (orderDispute.getDisputeStatus() == DisputeStatus.RESOLVED ||
                orderDispute.getDisputeStatus() == DisputeStatus.APPROVED)
        {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, getMessage("api_errors.order_dispute.already_processed"));
        }

        try {
            orderDispute.setAdminNotes(reason != null ? reason.trim() : "");

            if ("approve".equalsIgnoreCase(decision)) {
                log.info("Admin approved complaint return for order: {}. Upgrading tracking status rules.", orderNumber);
                orderDispute.setDisputeStatus(DisputeStatus.RETURN_UPHELD);
                OrderDispute updatedItem= orderDisputeRepo.save(orderDispute);

                //CONFIRM ITEM RECEIPT IF RETURN NOT JUSTIFIED
                ListingOrderItem item = orderDispute.getListingOrderItem();
                item.setReceiptConfirmationStatus(ReceiptConfirmationStatus.CONFIRMED);
                listingOrderItemRepo.save(item);

                //  CREATE REVERSE SHIPPING ORDER TRAILER
                // shippingService.createReverseReturnLabel(orderDispute);
                Session session = stripePaymentProviderService.createEmbeddedConnectCheckoutSessionShipmentCostURL(orderDispute,
                        LocaleContextHolder.getLocale().getLanguage());
                String paymentURL = paymentService.createReturnShippingPayment(session,orderDispute).getData();
                //notify seller
                String email = orderDispute.getListingOrderItem().getSeller().getUser().getUsername();
                emailNotificationService.sendReturnShippingPaymentRequest(email,orderNumber, orderDispute.getTicketId(),paymentURL);

                String buyerEmail = orderDispute.getListingOrderItem().getSeller().getUser().getUsername();
                emailNotificationService.sendAdminResponseToBuyerComplaint(buyerEmail,orderNumber,decision,reason);

                return ResponseDto.builder()
                        .data(updatedItem.getId())
                        .httpStatus(HttpStatus.ACCEPTED.value())
                        .build();

            }
            else if ("reject".equalsIgnoreCase(decision)) {
                log.info("Admin rejected complaint counter-offer for order: {}. seller payout cleared.", orderNumber);
                // Escalates the case to platform administrators for formal mediation review
                orderDispute.setDisputeStatus(DisputeStatus.REJECTED);
                orderDisputeRepo.save(orderDispute);

                //clear seller for payment
                SellerPayout sellerPayout= sellerPayoutRepo.findByListingOrderItemId(orderDispute.getListingOrderItem().getId())
                        .orElse(null);

                if(sellerPayout!=null){
                    sellerPayout.setStatus(PayoutStatus.APPROVED);
                    sellerPayoutRepo.save(sellerPayout);
                }

                String buyerEmail = orderDispute.getListingOrderItem().getSeller().getUser().getUsername();
                emailNotificationService.sendAdminResponseToBuyerComplaint(buyerEmail,orderNumber,decision,reason);
                return ResponseDto.builder()
                        .data(null)
                        .httpStatus(HttpStatus.OK.value())
                        .build();
            }
            else {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, getMessage("api_errors.order_dispute.invalid_decision"));
            }


        } catch (ResponseStatusException e) {
            // Rethrow specific API exceptions to pass your customized error message headers straight down to your React alerts
            throw e;
        } catch (Exception e) {

            // to ensure Spring's transaction interceptor executes a 100% clean database rollback!
            log.error("Fatal failure executing seller transaction update for dispute on order {}: {}", orderNumber, e.getMessage(), e);
            throw new RuntimeException(getMessage("api_errors.order_dispute.processing_failed"), e);
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
                .disputeStatus(DisputeStatus.OPEN)
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
