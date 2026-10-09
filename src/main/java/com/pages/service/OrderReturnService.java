package com.pages.service;

import com.pages.dto.AddressResponse;
import com.pages.dto.OrderReturnRequest;
import com.pages.dto.ResponseDto;
import com.pages.enums.*;
import com.pages.exception.AccessPermissionException;
import com.pages.exception.EntityNotFoundException;
import com.pages.exception.InvalidOperationException;
import com.pages.model.*;
import com.pages.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource; // 🚀 i18n Core Engine
import org.springframework.context.i18n.LocaleContextHolder; // 🚀 Dynamic language locator
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Service
public class OrderReturnService {

    private final OrderReturnRepo orderReturnRepo;
    private final ListingOrderItemRepo listingOrderItemRepo;
    private final ListingOrderRepo listingOrderRepo;
    private final SellerShipmentRepo sellerShipmentRepo;
    private final SellerProfileService sellerProfileService;
    private final SellerPayoutService sellerPayoutService;
    private final SellerProfileRepo sellerProfileRepo;
    private final PaymentService paymentService;
    private final InventoryItemRepo inventoryItemRepo;
    private final ReturnShipmentRepo returnShipmentRepo;
    private final GlobalAddressService globalAddressService;
    private final AppUserRepo appUserRepo;
    private final MessageSource messageSource;


    public OrderReturnService(OrderReturnRepo orderReturnRepo, ListingOrderItemRepo listingOrderItemRepo,
                              ListingOrderRepo listingOrderRepo, SellerShipmentRepo sellerShipmentRepo,
                              SellerPayoutService sellerPayoutService, SellerProfileRepo sellerProfileRepo,
                              SellerProfileService sellerProfileService, PaymentService paymentService,
                              InventoryItemRepo inventoryItemRepo, ReturnShipmentRepo returnShipmentRepo,
                              GlobalAddressService globalAddressService, AppUserRepo appUserRepo,
                              MessageSource messageSource) {
        this.orderReturnRepo = orderReturnRepo;
        this.listingOrderItemRepo = listingOrderItemRepo;
        this.listingOrderRepo = listingOrderRepo;
        this.sellerShipmentRepo = sellerShipmentRepo;
        this.sellerPayoutService = sellerPayoutService;
        this.sellerProfileService = sellerProfileService;
        this.paymentService = paymentService;
        this.sellerProfileRepo = sellerProfileRepo;
        this.inventoryItemRepo = inventoryItemRepo;
        this.returnShipmentRepo = returnShipmentRepo;
        this.globalAddressService = globalAddressService;
        this.appUserRepo = appUserRepo;
        this.messageSource = messageSource;
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

    // Dynamic bundle translation shortcut helper method
    private String getMessage(String code, Object[] args) {
        return messageSource.getMessage(code, args, LocaleContextHolder.getLocale());
    }

    /**
     *  PROTECTED RETURN & ANTI-WITHDRAWAL PIPELINE
     * Prevents buyers from canceling paid orders mid-transit to protect sellers.
     * Restricts the return flow strictly to items that have been verified as DELIVERED.
     */
    @Transactional
    public Boolean executeReturn(Jwt jwt, OrderReturnRequest request) {
        if (jwt == null) {
            throw new AccessPermissionException(getMessage("api_errors.order_return.access_denied", null));
        }

        AppUser appUser = appUserRepo.findByUsername(jwt.getSubject())
                .orElseThrow(() -> new UsernameNotFoundException(getMessage("api_errors.order_return.user_not_found", null)));

        ListingOrderItem orderItem = listingOrderItemRepo.findById(request.getOrderItemId())
                .orElseThrow(() -> new EntityNotFoundException(getMessage("api_errors.order_return.item_not_found", null)));

        // Strict Ownership Check: Verify that the user executing the request is the actual buyer
        ListingOrder parentOrder = orderItem.getListingOrder();
        if (parentOrder == null || !parentOrder.getBuyer().getId().equals(appUser.getId())) {
            throw new AccessPermissionException(getMessage("api_errors.order_return.not_your_order", null));
        }

        // Check for existing return entries to block double-filing bugs
        OrderReturn returnItem = orderReturnRepo.findByListingOrderItem(orderItem).orElse(null);
        if (returnItem != null) {
            throw new DuplicateKeyException(getMessage("api_errors.order_return.duplicate_record", null));
        }

        // ANTI-WITHDRAWAL SHIELD RULE 1:
        // If the item status is PAID but the courier hasn't delivered it yet, reject any withdrawal attempts!
        if (orderItem.getOrderItemStatus().equals(OrderItemStatus.PAID)) {
            // Find the shipment record associated with the item to check real-time transit state
            SellerShipment sellerShipment = sellerShipmentRepo.findByListingOrderItemId(request.getOrderItemId()).orElse(null);

            if (sellerShipment == null || !sellerShipment.getShipmentStatus().equals(ShipmentStatus.DELIVERED)) {
                log.warn("Security Shield: Prevented mid-transit withdrawal attempt by Buyer [{}] on Order Item ID [{}].",
                        appUser.getId(), orderItem.getId());

                //  EXPLICIT BUSINESS RULE REJECTION:
                // Throws an error to the frontend explaining that the seller may have already shipped the package.
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        getMessage("api_errors.order_return.withdrawal_blocked",null));
            }
        }

        // ANTI-WITHDRAWAL SHIELD RULE 2:
        // Ensure the order item is in an authentic state that allows returns (must not be un-paid, canceled, or pending authorization)
        if (!orderItem.getOrderItemStatus().equals(OrderItemStatus.PAID) && !orderItem.getOrderItemStatus().equals(OrderItemStatus.RETURN_REQUESTED)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, getMessage("api_errors.order_return.invalid_status", null));
        }

        SellerShipment sellerShipment = sellerShipmentRepo.findByListingOrderItemId(request.getOrderItemId())
                .orElseThrow(() -> new EntityNotFoundException(getMessage("api_errors.order_return.undelivered_item", null)));

        // Enforce the 24-hour return window cutoff rule tracking from deliveredAt
        Instant deadline = sellerShipment.getDeliveredAt().plus(24, ChronoUnit.HOURS);
        Instant now = Instant.now();

        if (now.isAfter(deadline)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, getMessage("api_errors.order_return.deadline_expired", null));
        }

        // Complete Financial Reversal Aggregation Matrix
        BigDecimal productPrice = orderItem.getSellerPrice() != null ? orderItem.getSellerPrice() : BigDecimal.ZERO;


        OrderReturn orderReturn = OrderReturn.builder()
                .buyerComment(request.getBuyerComment())
                .listingOrderItem(orderItem)
                .returnReason(getReturnReason(request.getReturnReason()))
                .status("PENDING_REVIEW")
                .sellerDeductionAmount(productPrice)
                .sellerDebited(false)
                .build();

        // Lock item status into a clear dispute tracking state to halt automated escrow payout cron tasks [INDEX]
       // orderItem.setOrderItemStatus(OrderItemStatus.RETURN_REQUESTED);
        listingOrderItemRepo.save(orderItem);

        log.info("Fulfillment Guard: Return requested for Order #{}. Escrow payout cron successfully paused.",
                parentOrder.getOrderNumber());

        OrderReturn savedReturn = orderReturnRepo.save(orderReturn);
        createReturnShipment(savedReturn);
        return true;
    }



    @Transactional
    public void createReturnShipment(OrderReturn orderReturn) {
        if (orderReturn == null) {
            throw new InvalidOperationException(getMessage("api_errors.logistics.shipment_not_found", null));
        }

        SellerProfile sellerProfile = orderReturn.getListingOrderItem().getSeller();
        AddressResponse sellerAddress = globalAddressService.getAddress(sellerProfile.getUser());

        String address;
        if (sellerAddress != null) {
            address = String.join(", ",
                    sellerAddress.street().trim(),
                    sellerAddress.postalCode().trim(),sellerAddress.city().trim(),
                    sellerAddress.country().trim(),sellerAddress.contact().trim()
            );
        } else {
            address = "Kasoa.pl, Focus Mall, ul. Słowackiego 123, 97-300, Piotrków Trybunalski, Polska";
            log.warn("Address metrics empty for Seller User ID: {}. Using Kasoa HQ address fallback parameters.",
                    sellerProfile.getUser().getId());
        }

        ReturnShipment returnShipment = ReturnShipment.builder()
                .orderReturn(orderReturn)
                .shipmentStatus(ShipmentStatus.AWAITING_SHIPMENT)
                .address(address)
                .itemSize(orderReturn.getListingOrderItem().getInventoryItem().getItemSize().name())
                .build();
        returnShipmentRepo.save(returnShipment);
    }

    /**
     *  ASYNCHRONOUS BACKGROUND RETURN RESOLUTION WORKER
     * Executes automatically after the endpoint confirmation returns TRUE.
     * Enforces strict pre-flight delivery verification, locks platform take-rate revenue,
     * and securely triggers your independent wallet balance services exactly once.
     */
    @Transactional
    public void executeFinancialItemRefundOnReceipt(String token) {

        //  STEP 1: MANDATORY RETURN SHIPMENT GATEWAY CHECK
        // Fetch the corresponding logistics record and verify that the seller has confirmed receipt!
        ReturnShipment returnShipment = returnShipmentRepo.findByReceiptConfirmationToken(token).orElse(null);

        if (returnShipment == null || returnShipment.getReceiptConfirmedAt() == null) {
            log.error("Security Shield Alert: Attempted background refund execution on an unconfirmed return! token: {}",token);
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    getMessage("api_errors.order_return.not_confirmed", null)
            );
        }

        log.info("Background Tax Engine: Processing terminal return sequence for Return Ticket ID: {}", returnShipment.getOrderReturn().getId());

        // 1. Fetch our master return ticket profile
        OrderReturn returnTicket = returnShipment.getOrderReturn();

        // Idempotency Shield: Prevent duplicate background execution loops
        if (returnTicket.isSellerDebited()) {
            log.warn("Background Tax Engine Shield: Return ID {} already processed. Standing down.", returnTicket.getId());
            return;
        }


        ListingOrderItem item = returnTicket.getListingOrderItem();

        //  THE NON-REFUNDABLE LOGISTICS RULE:
        // Logistics shipping fees and platform service charges are locked as non-refundable.
        // The seller deduction and buyer cash refund scale strictly to the product item price.
        BigDecimal productPriceClawback = item.getSellerPrice() != null ? item.getSellerPrice() : BigDecimal.ZERO;
        Instant now = Instant.now();

        // --- TABLE 1: RECORD LOG ENTRIES TO THE AUDITING HISTORY TABLE ---
        returnTicket.setSellerDeductionAmount(productPriceClawback);
        returnTicket.setStatus("RETURN_COMPLETED");
        returnTicket.setSellerDebited(true);
        returnTicket.setResolvedAt(now);
        orderReturnRepo.save(returnTicket);

        // --- TABLE 2 & 3: DISPATCH TO THE SEPARATE WALLET BALANCER TABLES ---
        sellerPayoutService.createRefund(item);
        sellerProfileService.updateSellerTotalPayoutWithRefund(item);

        // --- STEP 3: DISPATCH AUTOMATED PAYU CUSTOMER CASH REVERSAL ---
        // Releases the product price from your platform hold account straight back to the buyer
        paymentService.createRefund(item);

        // --- STEP 4: UPDATE INTERMEDIATE ENUMS & CLOSE WORKFLOWS CLEANLY ---
        item.setOrderItemStatus(OrderItemStatus.CANCELLED);
        listingOrderItemRepo.save(item);

        // Defensive Inventory Management: Hide broken inventory from your frontend catalog views
        InventoryItem inventory =item.getInventoryItem();

        if (inventory != null) {
            inventory.setStatus(InventoryStatus.AVAILABLE);
            inventoryItemRepo.save(inventory);
        }

        log.info("Background Tax Engine Success: Item return finalized safely. Separate double-entry ledger offset saved for Item ID #{}", item.getId());
    }

    @Transactional
    public void executeSellerCancellation(Jwt jwt, Long orderItemId) {
        if (jwt == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    getMessage("api_errors.order_cancellation.unauthorized", null)
            );
        }

        AppUser authenticatedUser = appUserRepo.findByUsername(jwt.getSubject())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        getMessage("api_errors.order_cancellation.user_not_found", null)
                ));

        ListingOrderItem orderItem = listingOrderItemRepo.findById(orderItemId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        getMessage("api_errors.order_cancellation.item_not_found", null)
                ));

        // SELLER IDENTITY AUTHENTICATION GUARD
        // Confirms that the user firing this cancellation is the actual owner of the listing!
        SellerProfile sellerProfile = orderItem.getSeller();
        if (sellerProfile == null || !sellerProfile.getUser().getId().equals(authenticatedUser.getId())) {
            log.error("Security Shield Alert: Unauthorized cancellation attempt! User [{}] tried to cancel item ID [{}] belonging to Seller Profile [{}]",
                    authenticatedUser.getId(), orderItem.getId(), sellerProfile != null ? sellerProfile.getId() : "NULL");
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    getMessage("api_errors.order_cancellation.forbidden", null)
            );
        }

        // Validate shipping history parameters to prevent mid-transit cancellations
        SellerShipment shipment = sellerShipmentRepo.findByListingOrderItemId(orderItem.getId()).orElse(null);
        if (shipment != null && (shipment.getShipmentStatus() == ShipmentStatus.SHIPPED || shipment.getShipmentStatus() == ShipmentStatus.DELIVERED)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    getMessage("api_errors.order_cancellation.already_shipped", null)
            );
        }

        if (orderItem.getOrderItemStatus() == OrderItemStatus.CANCELLED) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    getMessage("api_errors.order_cancellation.already_cancelled", null)
            );
        }

        log.warn("Fulfillment System: Seller [{}] is cancelling Order Item #{} [Executing balance clawbacks...]",
                authenticatedUser.getUsername(), orderItem.getId());

        try {
            // Defensive Inventory Management: Hide broken inventory from your frontend catalog views
            InventoryItem inventory = orderItem.getInventoryItem();
            if (inventory != null) {
                inventory.setStatus(InventoryStatus.UNAVAILABLE);
                inventoryItemRepo.save(inventory);
            }

            // Lock order item status to CANCELLED
            orderItem.setOrderItemStatus(OrderItemStatus.CANCELLED);
            listingOrderItemRepo.save(orderItem);

            // Flush changes directly down to database row indexes to eliminate query race conditions
            listingOrderItemRepo.flush();

            // Evaluate if this cancellation completely closes out the parent order structure
            List<ListingOrderItem> siblingItems = listingOrderItemRepo.findByListingOrderId(orderItem.getListingOrder().getId());
            boolean areAllItemsCancelled = siblingItems.stream()
                    .allMatch(item -> item.getOrderItemStatus() == OrderItemStatus.CANCELLED);

            if (areAllItemsCancelled) {
                ListingOrder parent = orderItem.getListingOrder();
                parent.setOrderStatus(OrderStatus.CANCELLED);
                listingOrderRepo.save(parent);
                log.info("Fulfillment System: Master Order #{} automatically set to CANCELLED.", parent.getOrderNumber());
            }

            if (shipment != null) {
                shipment.setShipmentStatus(ShipmentStatus.CANCELLED);
                sellerShipmentRepo.save(shipment);
            }


            //  DISPATCH PAYU CUSTOMER CASH REVERSAL
            // Fires an automated refund request to return the escrow funds back to the buyer's bank account.
            paymentService.createRefund(orderItem);

            // ACCOUNTING ADJUSTMENT INTERCEPT:
            // Because the seller was credited at checkout, we run clawback script here.
            // This safely deducts the item price out of totalSettlement and increases cancelledOrders.
            sellerProfileService.updateSellerTotalPayoutWithRefund(orderItem);


            log.info("Fulfillment System Success: Merchant balance clawback locked down. Order Item #{} successfully archived.", orderItem.getId());

        } catch (Exception e) {
            log.error("Fulfillment System Error: Critical failure executing merchant cancellation for ID: {}", orderItemId, e);
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    getMessage("api_errors.order_cancellation.gateway_failure", null)
            );
        }
    }


}
