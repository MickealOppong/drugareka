package com.pages.service;

import com.pages.dto.AddressResponse;
import com.pages.dto.OrderReturnRequest;
import com.pages.enums.*;
import com.pages.exception.AccessPermissionException;
import com.pages.exception.EntityNotFoundException;
import com.pages.exception.InvalidOperationException;
import com.pages.model.*;
import com.pages.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource; // 🚀 i18n Core Engine
import org.springframework.context.annotation.Lazy;
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
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderReturnService {

    private final ListingOrderItemRepo listingOrderItemRepo;
    private final ListingOrderRepo listingOrderRepo;
    private final SellerShipmentRepo sellerShipmentRepo;
    private final SellerProfileService sellerProfileService;
    private final PaymentRefundLedgerService paymentRefundLedgerService;
    private final InventoryItemRepo inventoryItemRepo;
    private final AppUserRepo appUserRepo;
    private final MessageSource messageSource;
    private final ListingTransactionRepo listingTransactionRepo;
    private final ServiceChargeService serviceChargeService;





    // Dynamic bundle translation shortcut helper method
    private String getMessage(String code) {
        return messageSource.getMessage(code, null, LocaleContextHolder.getLocale());
    }


    @Transactional
    public void executeSellerCancellation(Jwt jwt, Long orderItemId) {
        if (jwt == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, getMessage("api_errors.order_cancellation.unauthorized"));
        }

        AppUser authenticatedUser = appUserRepo.findByUsername(jwt.getSubject())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, getMessage("api_errors.order_cancellation.user_not_found")));

        // 1. DATABASE ISOLATION WORKER BOUNDARY: Verifies security parameters and applies row-level locking writes
        ListingOrderItem lockedItem = this.processSellerCancellationDbTx(authenticatedUser, orderItemId);

        try {
            // 2. THIRD-PARTY DISPATCH WORKFLOW: Executed strictly outside active database connection locks
            // Fires an automated refund request to return the escrow funds back to the buyer's account.
         paymentRefundLedgerService.createRefundFromSellerCancellation(lockedItem);

            // Process accounting balance adjustment clawbacks securely inside the database instance lock
            sellerProfileService.updateSellerTotalPayoutWithRefund(lockedItem);

            //reverse service charge
            serviceChargeService.reverseCharge(lockedItem);

            log.info("Fulfillment System Success: Order Item #{} successfully refunded and archived.", orderItemId);

        } catch (Exception e) {
            log.error("CRITICAL API FAILURE: Failed to issue external customer refund for item ID: {}. Triggering database recovery rollback sequence.", orderItemId, e);

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    getMessage("api_errors.order_cancellation.gateway_failure")
            );
        }
    }

    @Transactional
    public void executeBuyerCancellation(Jwt jwt, Long orderItemId) {
        if (jwt == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, getMessage("api_errors.order_cancellation.unauthorized"));
        }

        AppUser authenticatedUser = appUserRepo.findByUsername(jwt.getSubject())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, getMessage("api_errors.order_cancellation.user_not_found")));

        // 1. DATABASE ISOLATION WORKER BOUNDARY: Verifies security parameters and applies row-level locking writes
        ListingOrderItem lockedItem = this.processBuyerCancellationDbTx(authenticatedUser, orderItemId);

        try {
            // 2. THIRD-PARTY DISPATCH WORKFLOW: Executed strictly outside active database connection locks
            // Fires an automated refund request to return the escrow funds back to the buyer's account.
           paymentRefundLedgerService.createBuyerRefund(lockedItem);

            // Process accounting balance adjustment clawbacks securely inside the database instance lock
            sellerProfileService.updateSellerTotalPayoutWithRefund(lockedItem);

            //reverse service charge
            serviceChargeService.reverseCharge(lockedItem);

            log.info("Fulfillment System Success: Order Item #{} successfully refunded and archived.", orderItemId);

        } catch (Exception e) {
            log.error("CRITICAL API FAILURE: Failed to issue external customer refund for item ID: {}. Triggering database recovery rollback sequence.", orderItemId, e);

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    getMessage("api_errors.order_cancellation.gateway_failure")
            );
        }
    }




    @Transactional
    protected ListingOrderItem processSellerCancellationDbTx(AppUser authenticatedUser, Long orderItemId) {

        // Enforce pessimistic write lock to completely block duplicate concurrent button clicks
        ListingOrderItem orderItem = listingOrderItemRepo.findByIdForUpdate(orderItemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, getMessage("api_errors.order_cancellation.item_not_found")));

        SellerProfile sellerProfile = orderItem.getSeller();
// Only the seller or an admin can cancel the order.
        boolean isSeller = sellerProfile.getUser().getId()
                .equals(authenticatedUser.getId());

        boolean isAdmin = authenticatedUser.getUserRoles().stream()
                .anyMatch(role -> "ROLE_ADMIN".equals(role.getRole()));

        if (!isSeller && !isAdmin) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    getMessage("api_errors.order_cancellation.forbidden")
            );
        }

        SellerShipment shipment = sellerShipmentRepo.findByListingOrderItemId(orderItem.getId()).orElse(null);
        if (shipment != null && (shipment.getShipmentStatus() == ShipmentStatus.SHIPPED || shipment.getShipmentStatus() == ShipmentStatus.DELIVERED)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, getMessage("api_errors.order_cancellation.already_shipped"));
        }

        if (orderItem.getOrderItemStatus() == OrderItemStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, getMessage("api_errors.order_cancellation.already_cancelled"));
        }

        // Defensive Inventory Management: Hide broken inventory from catalog circulation
        InventoryItem inventory = orderItem.getInventoryItem();
        if (inventory != null) {
            // Lock inventory item row explicitly before modifying fields
            InventoryItem lockedInventory = inventoryItemRepo.findByIdForUpdate(inventory.getId()).orElse(null);

            if(lockedInventory!=null){
                lockedInventory.setStatus(InventoryStatus.AVAILABLE);
                inventoryItemRepo.save(lockedInventory);
            }

        }

        //change listing to draft
        listingTransactionRepo.findById(orderItem.getListingId()).ifPresent(listing->{
            listing.setListingStatus(ListingStatus.DRAFT);
            listingTransactionRepo.save(listing);
        });
        //cancel order
        orderItem.setOrderItemStatus(OrderItemStatus.CANCELLED);
        listingOrderItemRepo.save(orderItem);

        // Evaluate master order termination transitions
        List<ListingOrderItem> siblingItems = listingOrderItemRepo.findByListingOrderId(orderItem.getListingOrder().getId());
        boolean areAllItemsCancelled = siblingItems.stream()
                .allMatch(item -> item.getOrderItemStatus() == OrderItemStatus.CANCELLED);

        if (areAllItemsCancelled) {
            ListingOrder parent = orderItem.getListingOrder();
            parent.setOrderStatus(OrderStatus.CANCELLED);
            listingOrderRepo.save(parent);
        }

        if (shipment != null) {
            shipment.setShipmentStatus(ShipmentStatus.CANCELLED);
            sellerShipmentRepo.save(shipment);
        }

        return orderItem;
    }

    @Transactional(rollbackFor = Exception.class)
    protected ListingOrderItem processBuyerCancellationDbTx(AppUser authenticatedUser, Long orderItemId) {

        // Enforce pessimistic write lock to completely block duplicate concurrent button clicks
        ListingOrderItem orderItem = listingOrderItemRepo.findByIdForUpdate(orderItemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, getMessage("api_errors.order_cancellation.item_not_found")));


        SellerShipment shipment = sellerShipmentRepo.findByListingOrderItemId(orderItem.getId())
                .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND, getMessage("api_errors.order_cancellation.item_not_found")));

       ShipmentConfirmationStatus shipmentConfirmation = shipment.getSellerShipmentToken()!=null?shipment.getSellerShipmentToken()
               .getShipmentConfirmationStatus():null;

        //only buyer or admin can cancel order
        boolean isBuyer =!orderItem.getListingOrder().getBuyer().getId().equals(authenticatedUser.getId());
        boolean isAdmin  = authenticatedUser.getUserRoles().stream().anyMatch(role->role.getRole().equals("ROLE_ADMIN"));

        if(!isBuyer && !isAdmin){
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, getMessage("api_errors.order_cancellation.forbidden"));
        }

        if(shipmentConfirmation !=null && !isAdmin){
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, getMessage("api.errors.order_cancellation.confirmed"));
        }
        if (shipment.getShipmentStatus() == ShipmentStatus.SHIPPED || shipment.getShipmentStatus() == ShipmentStatus.DELIVERED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, getMessage("api_errors.order_cancellation.already_shipped"));
        }



        if (orderItem.getOrderItemStatus() == OrderItemStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, getMessage("api_errors.order_cancellation.already_cancelled"));
        }

        // Defensive Inventory Management: Hide broken inventory from catalog circulation
        InventoryItem inventory = orderItem.getInventoryItem();
        if (inventory != null) {
            // Lock inventory item row explicitly before modifying fields
            InventoryItem lockedInventory = inventoryItemRepo.findByIdForUpdate(inventory.getId()).orElse(null);

            if(lockedInventory!=null){
                lockedInventory.setStatus(InventoryStatus.AVAILABLE);
                inventoryItemRepo.save(lockedInventory);
            }

        }

        //cancel order
        orderItem.setOrderItemStatus(OrderItemStatus.CANCELLED);
        listingOrderItemRepo.save(orderItem);

        // Evaluate master order termination transitions
        List<ListingOrderItem> siblingItems = listingOrderItemRepo.findByListingOrderId(orderItem.getListingOrder().getId());
        boolean areAllItemsCancelled = siblingItems.stream()
                .allMatch(item -> item.getOrderItemStatus() == OrderItemStatus.CANCELLED);

        if (areAllItemsCancelled) {
            ListingOrder parent = orderItem.getListingOrder();
            parent.setOrderStatus(OrderStatus.CANCELLED);
            listingOrderRepo.save(parent);
        }


            shipment.setShipmentStatus(ShipmentStatus.CANCELLED);
            sellerShipmentRepo.save(shipment);


        return orderItem;
    }

    @Transactional(rollbackFor = Exception.class)
    protected void revertSellerCancellationDbTx(Long orderItemId) {
        log.warn("Executing data compensation: Restoring state parameters for order item ID: {}", orderItemId);

        ListingOrderItem orderItem = listingOrderItemRepo.findById(orderItemId).orElse(null);
        if(orderItem==null){
            return;
        }
        orderItem.setOrderItemStatus(OrderItemStatus.PAID); // Restore order track validation mapping
        listingOrderItemRepo.save(orderItem);

        InventoryItem inventory = orderItem.getInventoryItem();
        if (inventory != null) {
            InventoryItem lockedInventory = inventoryItemRepo.findByIdForUpdate(inventory.getId()).orElse(null);
          if(lockedInventory!=null){
              lockedInventory.setStatus(InventoryStatus.SOLD); // Re-lock product back into stock catalog
              inventoryItemRepo.save(lockedInventory);
          }
        }

        ListingOrder parent = orderItem.getListingOrder();
        if (parent.getOrderStatus() == OrderStatus.CANCELLED) {
            parent.setOrderStatus(OrderStatus.PAID);
            listingOrderRepo.save(parent);
        }

        sellerShipmentRepo.findByListingOrderItemId(orderItemId).ifPresent(shipment -> {
            shipment.setShipmentStatus(ShipmentStatus.AWAITING_SHIPMENT); // Put back to active packing state
            sellerShipmentRepo.save(shipment);
        });

    }

    @Transactional(rollbackFor = Exception.class)
    protected void revertBuyerCancellationDbTx(Long orderItemId) {
        log.warn("Executing data compensation: Restoring state parameters for order item ID: {}", orderItemId);

        ListingOrderItem orderItem = listingOrderItemRepo.findById(orderItemId).orElse(null);
        if(orderItem==null){
            return;
        }
        orderItem.setOrderItemStatus(OrderItemStatus.PAID); // Restore order track validation mapping
        listingOrderItemRepo.save(orderItem);

        InventoryItem inventory = orderItem.getInventoryItem();
        if (inventory != null) {
            InventoryItem lockedInventory = inventoryItemRepo.findByIdForUpdate(inventory.getId()).orElse(null);
            if(lockedInventory!=null){
                lockedInventory.setStatus(InventoryStatus.SOLD); // Re-lock product back into stock catalog
                inventoryItemRepo.save(lockedInventory);
            }
        }

        ListingOrder parent = orderItem.getListingOrder();
        if (parent.getOrderStatus() == OrderStatus.CANCELLED) {
            parent.setOrderStatus(OrderStatus.PAID);
            listingOrderRepo.save(parent);
        }

        sellerShipmentRepo.findByListingOrderItemId(orderItemId).ifPresent(shipment -> {
            shipment.setShipmentStatus(ShipmentStatus.AWAITING_SHIPMENT); // Put back to active packing state
            sellerShipmentRepo.save(shipment);
        });
    }



}
