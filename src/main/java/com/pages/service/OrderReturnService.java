package com.pages.service;

import com.pages.dto.AddressResponse;
import com.pages.dto.OrderReturnRequest;
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

    @Transactional
    public Boolean executeReturn(Jwt jwt, OrderReturnRequest request) {
        if (jwt == null) {
            throw new AccessPermissionException(getMessage("api_errors.order_return.access_denied", null));
        }

        AppUser appUser = appUserRepo.findByUsername(jwt.getSubject())
                .orElseThrow(() -> new UsernameNotFoundException(getMessage("api_errors.order_return.user_not_found", null)));

        log.info("Creating return for user :{} id: {}", appUser.getUsername(), appUser.getId());

        ListingOrderItem orderItem = listingOrderItemRepo.findById(request.getOrderItemId())
                .orElseThrow(() -> new EntityNotFoundException(getMessage("api_errors.order_return.item_not_found", null)));

        OrderReturn returnItem = orderReturnRepo.findByListingOrderItem(orderItem).orElse(null);
        if (returnItem != null) {
            throw new DuplicateKeyException(getMessage("api_errors.order_return.duplicate_record", null));
        }

        if (!orderItem.getOrderItemStatus().equals(OrderItemStatus.PAID)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, getMessage("api_errors.order_return.unpaid_item", null));
        }

        SellerShipment sellerShipment = sellerShipmentRepo.findByListingOrderItemId(request.getOrderItemId())
                .orElseThrow(() -> new EntityNotFoundException(getMessage("api_errors.order_return.undelivered_item", null)));

        if (!sellerShipment.getShipmentStatus().equals(ShipmentStatus.DELIVERED)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, getMessage("api_errors.order_return.delivery_pending", null));
        }

        Instant deadline = sellerShipment.getDeliveredAt().plus(3, ChronoUnit.DAYS);
        Instant now = Instant.now();

        if (now.isAfter(deadline)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, getMessage("api_errors.order_return.deadline_expired", null));
        }

        OrderReturn orderReturn = OrderReturn.builder()
                .buyerComment(request.getBuyerComment())
                .listingOrderItem(orderItem)
                .returnReason(getReturnReason(request.getReturnReason()))
                .status("PENDING_REVIEW")
                .sellerDebited(false)
                .build();

        orderItem.setOrderItemStatus(OrderItemStatus.PROCESSING);
        listingOrderItemRepo.save(orderItem);

        log.info("Successfully filed rapid return ticket for Order #{}. Payout cron paused safely.",
                orderItem.getListingOrder().getOrderNumber());

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

    @Transactional
    public void executeFinancialItemRefundOnReceipt(Long returnId) {
        OrderReturn returnTicket = orderReturnRepo.findById(returnId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, getMessage("api_errors.order_return.ticket_not_found", null)));

        if (returnTicket.isSellerDebited()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, getMessage("api_errors.order_return.already_processed", null));
        }

        ListingOrderItem item = returnTicket.getListingOrderItem();
        BigDecimal itemClawbackTotal = item.getSubtotal().add(item.getShippingCost());
        returnTicket.setSellerDeductionAmount(itemClawbackTotal);

        SellerProfile seller = sellerProfileRepo.findById(item.getSeller().getId()).orElse(null);
        if (seller != null) {
            BigDecimal currentSales = seller.getTotalSales() != null ? seller.getTotalSales() : BigDecimal.ZERO;
            seller.setTotalSales(currentSales.subtract(itemClawbackTotal));
            sellerProfileRepo.save(seller);
            log.info("Partial Return: Clawed back {} PLN from Seller Profile ID: {}", itemClawbackTotal, seller.getId());
        }

        paymentService.createRefund(item);

        item.setOrderItemStatus(OrderItemStatus.CANCELLED);
        listingOrderItemRepo.save(item);

        returnTicket.setStatus("RETURN_COMPLETED");
        returnTicket.setSellerDebited(true);
        returnTicket.setResolvedAt(Instant.now());
        orderReturnRepo.save(returnTicket);

        sellerPayoutService.createRefund(item);
        sellerProfileService.updateSellerTotalPayoutWithRefund(item);

        log.info("Item return finalized safely. Negative double-entry ledger offset saved for Item ID #{}", item.getId());
    }

    @Transactional
    public void executeSellerCancellation(Jwt jwt, Long orderItemId) {
        if (jwt == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    getMessage("api_errors.order_cancellation.unauthorized", null)
            );
        }

        ListingOrderItem order = listingOrderItemRepo.findById(orderItemId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        getMessage("api_errors.order_cancellation.item_not_found", null)
                ));

        SellerShipment shipment = sellerShipmentRepo
                .findByListingOrderItemId(order.getId())
                .orElse(null);

        if (shipment != null
                && (shipment.getShipmentStatus() == ShipmentStatus.SHIPPED
                || shipment.getShipmentStatus() == ShipmentStatus.DELIVERED)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    getMessage("api_errors.order_cancellation.already_shipped", null)
            );
        }

        if (order.getOrderItemStatus() == OrderItemStatus.CANCELLED) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    getMessage("api_errors.order_cancellation.already_cancelled", null)
            );
        }

        log.info(
                "Seller {} initiated cancellation for Order Item #{}",
                order.getSeller().getUser().getUsername(),
                order.getId()
        );

        try {
            InventoryItem inventory = order.getInventoryItem();

            if (inventory != null) {
                inventory.setStatus(InventoryStatus.AVAILABLE);
                inventoryItemRepo.save(inventory);
            }

            order.setOrderItemStatus(OrderItemStatus.CANCELLED);
            listingOrderItemRepo.save(order);

            List<ListingOrderItem> items =
                    listingOrderItemRepo.findByListingOrderId(order.getListingOrder().getId());

            boolean areAllItemsCancelled = items.stream()
                    .allMatch(item -> item.getOrderItemStatus() == OrderItemStatus.CANCELLED);

            if (areAllItemsCancelled) {
                ListingOrder parent = order.getListingOrder();
                parent.setOrderStatus(OrderStatus.CANCELLED);
                listingOrderRepo.save(parent);

                log.info(
                        "Master Order #{} automatically set to CANCELLED because all sub-items are cancelled.",
                        parent.getOrderNumber()
                );
            } else {
                log.info(
                        "Master Order #{} remains active. Partial fulfillment loop ongoing.",
                        order.getListingOrder().getOrderNumber()
                );
            }

            if (shipment != null) {
                shipment.setShipmentStatus(ShipmentStatus.CANCELLED);
                sellerShipmentRepo.save(shipment);
            }

            sellerProfileService.updateSellerTotalPayoutWithRefund(order);
            paymentService.createRefund(order);

            log.info(
                    "Order Item #{} has been successfully cancelled and archived by merchant.",
                    order.getId()
            );

        } catch (Exception e) {
            log.error(
                    "Failed to safely execute order cancellation transaction pipeline for ID: {}",
                    orderItemId,
                    e
            );

            String dynamicGatewayError = getMessage(
                    "api_errors.order_cancellation.gateway_failure",
                    new Object[]{e.getMessage()}
            );

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    dynamicGatewayError
            );
        }
    }
}
