package com.pages.service;

import com.pages.dto.*;
import com.pages.enums.*;
import com.pages.exception.AccessPermissionException;
import com.pages.exception.EntityNotFoundException;
import com.pages.model.*;
import com.pages.repository.*;
import com.stripe.model.checkout.Session;
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
                orderDispute.setDisputeStatus(DisputeStatus.AWAITING_RETURN_PAYMENT);
                OrderDispute updatedItem= orderDisputeRepo.save(orderDispute);
/*
                Session session = stripePaymentProviderService.createEmbeddedConnectCheckoutSessionShipmentCostURL(orderDispute,
                        LocaleContextHolder.getLocale().getLanguage());
                String paymentURL = paymentService.createReturnShippingPayment(session,orderDispute).getData();
                //notify seller
                String email = orderDispute.getListingOrderItem().getSeller().getUser().getUsername();
                emailNotificationService.sendReturnShippingPaymentRequest(email,orderNumber, orderDispute.getTicketId(),paymentURL);

 */
                String email = orderDispute.getListingOrderItem().getSeller().getUser().getUsername();

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

                String email = orderDispute.getListingOrderItem().getSeller().getUser().getUsername();

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
        if (jwt == null) {
            throw new AccessPermissionException(getMessage("api_errors.order_return.access_denied"));
        }

        AppUser appUser = appUserRepo.findByUsername(jwt.getSubject())
                .orElseThrow(() -> new UsernameNotFoundException(getMessage("api_errors.order_return.user_not_found")));



        if(request.getImages()==null || request.getImages().length==0){
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, getMessage("api_errors.order_return.image_not_found"));
        }
        ListingOrderItem orderItem = listingOrderItemRepo.findByIdForUpdate(request.getOrderItemId())
                .orElseThrow(() -> new EntityNotFoundException(getMessage("api_errors.order_return.item_not_found")));

        if(orderItem.getReceiptConfirmationStatus()==ReceiptConfirmationStatus.CONFIRMED){
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, getMessage("api_errors.order_dispute.transaction_closed"));
        }

        // Strict Ownership Check: Verify that the user executing the request is the actual buyer
        ListingOrder parentOrder = orderItem.getListingOrder();
        if (parentOrder == null || !parentOrder.getBuyer().getId().equals(appUser.getId())) {
            throw new AccessPermissionException(getMessage("api_errors.order_return.not_your_order"));
        }

        // ANTI-WITHDRAWAL SHIELD RULE 1:
        // If the item status is PAID but the courier hasn't delivered it yet, reject any withdrawal attempts!
        if (!orderItem.getOrderItemStatus().equals(OrderItemStatus.PAID)) {
            // Find the shipment record associated with the item to check real-time transit state
            // Throws an error to the frontend explaining that the seller may have already shipped the package.
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    getMessage("api_errors.order_return.withdrawal_blocked"));
        }

        SellerShipment sellerShipment = sellerShipmentRepo.findByListingOrderItemId(request.getOrderItemId())
                .orElseThrow(()-> new ResponseStatusException(HttpStatus.BAD_REQUEST,getMessage("api_errors.order_return.access_denied")));



        if ( !sellerShipment.getShipmentStatus().equals(ShipmentStatus.DELIVERED)) {
            log.warn("Security Shield: Prevented mid-transit withdrawal attempt by Buyer [{}] on Order Item ID [{}].",
                    appUser.getId(), orderItem.getId());

            //  EXPLICIT BUSINESS RULE REJECTION:
            // Throws an error to the frontend explaining that the seller may have already shipped the package.
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    getMessage("api_errors.order_return.withdrawal_blocked"));
        }



        // Check for existing return entries to block double-filing bugs
        OrderDispute returnItem = orderDisputeRepo.findByListingOrderItem(orderItem).orElse(null);

        if (returnItem != null) {
            throw new DuplicateKeyException(getMessage("api_errors.order_return.duplicate_record"));
        }

        long hoursElapsed = ChronoUnit.HOURS.between(sellerShipment.getDeliveredAt(), Instant.now().plus(1,ChronoUnit.HOURS));
        if (hoursElapsed>24) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, getMessage("api_errors.order_return.deadline_expired"));
        }

        // 1. Generate a distinct, cryptographic public tracking reference string (Receipt)
        String ticketNumber = "TK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        Integer[] sortOrder = IntStream.range(0,request.getImages().length).boxed().toArray(Integer[]::new);

        OrderDispute orderReturn = OrderDispute.builder()
                .buyerComment(request.getBuyerComment())
                .listingOrderItem(orderItem)
                .returnReason(getReturnReason(request.getReturnReason()))
                .disputeStatus(DisputeStatus.OPEN)
                .orderNumber(request.getOrderNumber())
                .user(appUser)
                .ticketId(ticketNumber)
                .build();
        OrderDispute savedReturn = orderDisputeRepo.save(orderReturn);
        mediaService.uploadReturnImage(request.getImages(),sortOrder,savedReturn);

        //hold item confirmation
        orderItem.setReceiptConfirmationStatus(ReceiptConfirmationStatus.PENDING);
        listingOrderItemRepo.save(orderItem);

        //notify seller
        String email = orderReturn.getListingOrderItem().getSeller().getUser().getUsername();
        String orderNumber = savedReturn.getOrderNumber();
        String reason = savedReturn.getReturnReason().name();
        emailNotificationService.notifySellerOfBuyerReturnComplaint(email,orderNumber,reason);

        log.info("Fulfillment Guard: Return requested for Order #{}. Escrow payout cron successfully paused.",
                parentOrder.getOrderNumber());
        return true;
    }
}
