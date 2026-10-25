package com.pages.service;

import com.pages.dto.*;
import com.pages.enums.*;
import com.pages.model.*;
import com.pages.model.OrderConfirmationException;
import com.pages.repository.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.weaver.ast.Or;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


@Slf4j
@Service
@RequiredArgsConstructor
public class ListingOrderService {


    private final ListingOrderRepo listingOrderRepo;
    private final ListingOrderItemRepo listingOrderItemRepo;
    private final AppUserDetailsService appUserDetailsService;
    private final CartService cartService;
    private final InventoryItemRepo inventoryItemRepo;
    private final PayoutProcessingService payoutProcessingService;
    private final SellerProfileService sellerProfileService;
    private final SellerShipmentRepo sellerShipmentRepo;
    private final MessageSource messageSource;
    private final PaymentRepo paymentRepo;
    private final OrderConfirmationExceptionRepo orderConfirmationExceptionRepo;


    private String getMessage(String code) {
        return messageSource.getMessage(
                code,
                null,
                LocaleContextHolder.getLocale()
        );
    }

   @Transactional
    public ListingOrder createBuyerOrder(Jwt jwt) {
        if (jwt == null) {

            throw new IllegalArgumentException(getMessage("error.order.authenticate_failed"));
        }

        CartResponse cart = cartService.getCart(jwt);

        if (cart == null || cart.getCartItemList() == null || cart.getCartItemList().isEmpty()) {

            throw new IllegalStateException(getMessage("error.order.cart_empty"));
        }

        if (cart.getAddress() == null) {

            throw new IllegalStateException(getMessage("error.order.shipping_address_required"));
        }

        String permanentShippingAddress = String.join(
                ", ", cart.getAddress().street().trim(),
                cart.getAddress().postalCode().trim(),cart.getAddress().city().trim(),
                cart.getAddress().country().trim(),cart.getAddress().contact()
        );

        String orderNumber = "DR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        BigDecimal subTotal = cart.getCartItemList().stream()
                .map(CartItemDto::getPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);


        BigDecimal serviceCharge = cart.getCartItemList().stream()
                .map(CartItemDto::getServiceCharge)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal shippingCharge = cart.getCartItemList().stream()
                .map(CartItemDto::getShipping)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal orderTotal = serviceCharge.add(subTotal).add(shippingCharge);

        AppUser buyer = appUserDetailsService.getAppUserId(cart.getBuyerId());

        ListingOrder listingOrder = ListingOrder.builder()
                .orderNumber(orderNumber)
                .buyer(buyer)
                .buyerNameSnapshot(buyer.getFirstName()+" "+buyer.getLastName())
                .shippingAddress(permanentShippingAddress)
                .orderTotal(orderTotal)
                .currency("PLN")
                .orderStatus(OrderStatus.PROCESSING)
                .build();

        ListingOrder orderHeader = listingOrderRepo.save(listingOrder);


        for (CartItemDto cartItem : cart.getCartItemList()) {
            InventoryItem inventoryItem = inventoryItemRepo.findByIdForUpdate(cartItem.getInventoryId())
                    .orElseThrow(() -> new IllegalArgumentException(getMessage("error.order.product_not_found")));

            if (inventoryItem.getStatus() != InventoryStatus.AVAILABLE &&
                    !orderHeader.getBuyer().getId().equals(inventoryItem.getReservedBy())) {
                //throw new IllegalStateException("error.order.product_unavailable");
                cartService.deleteCartById(cart.getBuyerId());
                return null;
            }

            inventoryItem.setStatus(InventoryStatus.RESERVED);
            inventoryItem.setReservedBy(orderHeader.getBuyer().getId());
            inventoryItem.setReservedUntil(Instant.now().plus(15, ChronoUnit.MINUTES));
            inventoryItemRepo.save(inventoryItem);


            log.info("Compiling order item for seller User ID: {}", inventoryItem.getSeller().getUser().getId());

            String sellerName = inventoryItem.getSeller().getUser().getFirstName()+" "+inventoryItem.getSeller().getUser().getLastName();

            ListingOrderItem orderItem = ListingOrderItem.builder()
                    .listingOrder(orderHeader)
                    .listingId(cartItem.getListingId())
                    .seller(inventoryItem.getSeller())
                    .serviceCharge(cartItem.getServiceCharge())
                    .sellerNameSnapshot(sellerName)
                    .shippingMethod(ShippingMethod.DPD)
                    .productBrandSnapshot(inventoryItem.getProductCatalog().getBrand().getName())
                    .productConditionSnapshot(inventoryItem.getProductCondition().getName())
                    .productNameSnapshot(inventoryItem.getProductCatalog().getName())
                    .sellerPrice(cartItem.getPrice())
                    .inventoryItem(inventoryItem)
                    .shippingCost(cartItem.getShipping())
                    .orderItemStatus(OrderItemStatus.PROCESSING)
                    .build();

            orderHeader.addItem(orderItem);
        }

        orderHeader.setOrderStatus(OrderStatus.PROCESSING);

        return listingOrderRepo.save(orderHeader);
    }

    @Transactional
    public boolean validateItemDelivery(String token) {
        ListingOrderItem orderItem =
                listingOrderItemRepo
                        .findByReceiptConfirmationTokenForUpdate(token)
                        .orElse(null);

        if (orderItem == null) {
            return false;
        }

        if (orderItem.getReceiptConfirmationStatus()
                == ReceiptConfirmationStatus.CONFIRMED) {
            return true;
        }

        Instant now = Instant.now();
        Instant expiresAt =
                orderItem.getReceiptConfirmationTokenExpiresAt();

        if (expiresAt == null || !expiresAt.isAfter(now)) {
            return false;
        }

        if (orderItem.getReceiptConfirmedAt() != null) {
            return false;
        }

        orderItem.setReceiptConfirmedAt(now);
        orderItem.setReceiptConfirmationTokenExpiresAt(now);
        orderItem.setReceiptConfirmationStatus(
                ReceiptConfirmationStatus.CONFIRMED
        );

        ListingOrderItem savedItem = listingOrderItemRepo.save(orderItem);

        payoutProcessingService.approvedPayout(savedItem);

        return true;
    }


    @Transactional
    public void validateItemDeliveryAndApprovePayout() {

        List<ListingOrderItem> items =
                listingOrderItemRepo.findAllUnconfirmedPaidItemsForUpdate(
                        Instant.now(),
                        OrderItemStatus.PAID
                );

        if (items.isEmpty()) {
            return;
        }

        Instant now = Instant.now();

        for (ListingOrderItem item : items) {
            item.setReceiptConfirmedAt(now);
            item.setReceiptConfirmationStatus(
                    ReceiptConfirmationStatus.CONFIRMED
            );
            item.setReceiptConfirmationTokenExpiresAt(now);

            //log automatic confirmation
            OrderConfirmationException orderConfirmationException = OrderConfirmationException.builder()
                    .confirmedAt(now)
                    .status("CONFIRMED")
                    .orderItemId(item.getId())
                    .reason("Buyer did not confirm within the confirmation windown and did not file a complaint")
                            .build();
            orderConfirmationExceptionRepo.save(orderConfirmationException);

            listingOrderItemRepo.save(item);
            payoutProcessingService.approvedPayout(item);

        }
    }

    @Transactional
    public void cleanUpAbandonOrders(){
      List<ListingOrderItem> items = listingOrderItemRepo.findAllByOrderStatusForUpdate(OrderItemStatus.PROCESSING);
      if(items.isEmpty()){
          return;
      }
      for(ListingOrderItem item :items){
          log.info("Processing listing order item :{}",item.getId());

        List<Payment> payments = paymentRepo.findByListingOrderId(item.getListingId());

        //leave it to payment cleanup service if there exist a payment
        if(!payments.isEmpty()){
            continue;
        }

        //cancel order
        item.setOrderItemStatus(OrderItemStatus.CANCELLED);
        listingOrderItemRepo.save(item);

        //release inventory
        InventoryItem inventoryItem = item.getInventoryItem();
        if(inventoryItem.getStatus()==InventoryStatus.RESERVED){
            inventoryItem.setStatus(InventoryStatus.AVAILABLE);
            inventoryItem.setReservedBy(null);
            inventoryItem.setReservedUntil(null);
            inventoryItemRepo.save(inventoryItem);
        }
          log.info("Completed cancelling listing order item :{}",item.getId());
      }
    }

    public boolean isDeliveryConfirmedAlready(String token) {
        ListingOrderItem orderItem =
                listingOrderItemRepo
                        .findByReceiptConfirmationToken(token)
                        .orElse(null);

        if (orderItem == null) {
            return false;
        }
        return orderItem.getReceiptConfirmationStatus()==ReceiptConfirmationStatus.CONFIRMED;
    }

    public void save(ListingOrderItem listingOrderItem){
        listingOrderItemRepo.save(listingOrderItem);
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    private OrderPageDto getStoreOrders(Jwt jwt, int page , int size){
        if(jwt!=null){
            AppUser buyer = appUserDetailsService.getAppUserByUsername(jwt.getSubject());
            List<String> roles = jwt.getClaimAsStringList("ROLE");

            boolean isAdmin = roles.contains("ROLE_ADMIN");
            if(isAdmin){
                Pageable pageable = PageRequest.of(
                        page-1,
                        size,
                        Sort.by(Sort.Direction.DESC, "createdAt")
                );

                Page<OrderDto> orders= listingOrderItemRepo.findAll(pageable).map(order->{
                    String sellerName =appUserDetailsService.getUsernameById(order.getSeller().getUser().getId());

                    return OrderDto.builder()
                            .id(order.getListingOrder().getId())
                            .orderNumber(order.getListingOrder().getOrderNumber())
                            .price(order.getListingOrder().getOrderTotal())
                            .serviceCharge(order.getServiceCharge())
                            .orderStatus(order.getListingOrder().getOrderStatus())
                            .paidAt(order.getListingOrder().getPaidAt())
                            .buyer(order.getListingOrder().getBuyerNameSnapshot())
                            .currency(order.getListingOrder().getCurrency())
                            .seller(sellerName)
                            .build();
                });
                return OrderPageDto.builder()
                        .orders(orders.getContent())
                        .totalElements(orders.getTotalElements())
                        .totalPages(orders.getTotalPages())
                        .page(orders.getNumber())
                        .pageSize(orders.getSize())
                        .build();
            }
        }
        return OrderPageDto.builder()
                .build();
    }
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public OrderPageDto myPurchases(Jwt jwt, int page , int size){
        if(jwt!=null){
            AppUser buyer = appUserDetailsService.getAppUserByUsername(jwt.getSubject());
            Long buyerId = buyer.getId();
            Pageable pageable = PageRequest.of(page-1,
                    size,
                    Sort.by(Sort.Direction.DESC, "createdAt")
            );
            Page<OrderDto> orders =listingOrderRepo.findByBuyerId(buyerId, pageable).map(order->{
                BigDecimal shippingCost = order.getItems().stream().map(ListingOrderItem::getShippingCost)
                        .reduce(BigDecimal.ZERO,BigDecimal::add);
                BigDecimal totalValue = order.getItems().stream().map(ListingOrderItem::getSellerPrice)
                        .reduce(BigDecimal.ZERO,BigDecimal::add);
                BigDecimal totalServiceCharge = order.getItems().stream().map(ListingOrderItem::getServiceCharge)
                        .reduce(BigDecimal.ZERO,BigDecimal::add);
                return OrderDto.builder()
                        .id(order.getId())
                        .orderNumber(order.getOrderNumber())
                        .price(totalValue)
                        .orderStatus(order.getOrderStatus())
                        .currency(order.getCurrency())
                        .shipping(shippingCost)
                        .serviceCharge(totalServiceCharge)
                        .createdAt(order.getCreatedAt())
                        .seller("Private person")
                        .buyer(order.getBuyerNameSnapshot())
                        .paidAt(order.getPaidAt())
                        .build();
            });
            return OrderPageDto.builder()
                    .orders(orders.getContent())
                    .totalElements(orders.getTotalElements())
                    .totalPages(orders.getTotalPages())
                    .accumulatedItems(getAccumulatedItemsFromPage(orders.getNumber(),orders.getSize(),orders.getTotalElements()))
                    .page(orders.getNumber())
                    .pageSize(orders.getSize())
                    .build();
        }
        return OrderPageDto.builder()
                .build();
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
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public OrderPageDto storeOrders(Jwt jwt, int page , int size){
        if(jwt!=null){
            Pageable pageable = PageRequest.of(page-1,
                    size,
                    Sort.by(Sort.Direction.DESC, "createdAt")
            );
            Page<OrderDto> orders =listingOrderItemRepo.findAll( pageable).map(orderItem->{
                String buyer = appUserDetailsService.getUsernameById(orderItem.getListingOrder().getBuyer().getId());
                return OrderDto.builder()
                        .id(orderItem.getListingOrder().getId())
                        .orderNumber(orderItem.getListingOrder().getOrderNumber())
                        .price(orderItem.getSellerPrice())
                        .serviceCharge(orderItem.getServiceCharge())
                        .shipping(orderItem.getShippingCost())
                        .orderStatus(orderItem.getListingOrder().getOrderStatus())
                        .currency(orderItem.getListingOrder().getCurrency())
                        .createdAt(orderItem.getCreatedAt())
                        .seller(orderItem.getSellerNameSnapshot())
                        .buyer(buyer)
                        .paidAt(orderItem.getListingOrder().getPaidAt())
                        .build();
            });
            return OrderPageDto.builder()
                    .orders(orders.getContent())
                    .totalElements(orders.getTotalElements())
                    .totalPages(orders.getTotalPages())
                    .page(orders.getNumber())
                    .pageSize(orders.getSize())
                    .build();
        }
        return OrderPageDto.builder()
                .build();
    }
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public OrderPageDto mySales(Jwt jwt, int page , int size){
        if(jwt!=null){
            AppUser user = appUserDetailsService.getAppUserByUsername(jwt.getSubject());
            SellerProfile sellerProfile = sellerProfileService.getSellerProfile(user.getId());
            if(sellerProfile ==null){
                return OrderPageDto.builder().build();
            }
            Long sellerId =sellerProfile.getId();
            Pageable pageable = PageRequest.of(page-1,
                    size,
                    Sort.by(Sort.Direction.DESC, "createdAt")
            );
            Page<OrderDto> orders =listingOrderItemRepo.findBySellerId(sellerId, pageable).map(orderItem->{
                return OrderDto.builder()
                        .id(orderItem.getId())
                        .orderNumber(orderItem.getListingOrder().getOrderNumber())
                        .price(orderItem.getSellerPrice())
                        .serviceCharge(orderItem.getServiceCharge())
                        .shipping(orderItem.getShippingCost())
                        .orderItemStatus(orderItem.getOrderItemStatus())
                        .currency(orderItem.getListingOrder().getCurrency())
                        .createdAt(orderItem.getCreatedAt())
                        .seller(orderItem.getSellerNameSnapshot())
                        .buyer("Private person")
                        .paidAt(orderItem.getListingOrder().getPaidAt())
                        .build();
            });
            return OrderPageDto.builder()
                    .orders(orders.getContent())
                    .totalElements(orders.getTotalElements())
                    .totalPages(orders.getTotalPages())
                    .page(orders.getNumber())
                    .accumulatedItems(getAccumulatedItemsFromPage(orders.getNumber(),orders.getSize(),orders.getTotalElements()))
                    .pageSize(orders.getSize())
                    .build();
        }
        return OrderPageDto.builder()
                .build();
    }
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<OrderDto> myBuyingDetails(Jwt jwt,Long orderId){
        if(jwt!=null){
            List<OrderDto> dataToSend = new ArrayList<>();
            ListingOrder order =listingOrderRepo.findById(orderId).orElse(null);
            if(order!=null){
                String user = appUserDetailsService.getUsernameById(order.getBuyer().getId());
                for(ListingOrderItem item :order.getItems()){
                    SellerShipment shipment= sellerShipmentRepo.findByListingOrderItemId(item.getId()).orElse(null);
                    String trackingNumber  =null;
                    String shipmentStatus = "";
                    Instant updatedAt = null;
                    if(shipment!=null){
                        trackingNumber = shipment.getTrackingNumber();
                        shipmentStatus = shipment.getShipmentStatus().name();
                        if(shipmentStatus.equals(ShipmentStatus.DELIVERED.name())){
                            updatedAt = shipment.getDeliveredAt();
                        }else if(shipmentStatus.equals(ShipmentStatus.SHIPPED.name())){
                            updatedAt = shipment.getShippedAt();
                        }else{
                            updatedAt = shipment.getModifiedAt();
                        }
                    }else{
                        updatedAt = order.getModifiedAt();
                    }
                    OrderDto dto= OrderDto.builder()
                            .id(item.getId())
                            .orderNumber(order.getOrderNumber())
                            .price(item.getSellerPrice())
                            .serviceCharge(item.getServiceCharge())
                            .shipping(item.getShippingCost())
                            .orderStatus(order.getOrderStatus())
                            .currency(order.getCurrency())
                            .createdAt(item.getCreatedAt())
                            .trackingNumber(trackingNumber)
                            .deliveryStatus(shipmentStatus)
                            .orderItemStatus(item.getOrderItemStatus())
                            .deliveryUpdatedAt(updatedAt)
                            .seller("Private person")
                            .buyer(item.getListingOrder().getBuyerNameSnapshot())
                            .paidAt(order.getPaidAt())
                            .build();
                    dataToSend.add(dto);
                }
            }
            return dataToSend;
        }
        return List.of();
    }
    public Long totalOrders(Jwt jwt){
        if(jwt!=null){
            return listingOrderRepo.count();
        }
        return 0L;
    }
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public Long totalUserOrders(Jwt jwt){
        if(jwt!=null){
            Long currentUserId = appUserDetailsService.getAppUserByUsername(jwt.getSubject()).getId();
            return listingOrderRepo.countByBuyerIdAndOrderStatus(currentUserId,OrderStatus.PAID);
        }
        return 0L;
    }
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public Long mySalesCount(Jwt jwt){
        if(jwt!=null){
            AppUser appUser=  appUserDetailsService.getAppUserByUsername(jwt.getSubject());
            SellerProfile seller = sellerProfileService.getSellerProfile(appUser.getId());
            if(seller!=null){
                return  listingOrderItemRepo.countBySellerIdAndListingOrderOrderStatus(seller.getId(),OrderStatus.PAID);
            }
            return 0L;
        }
        return 0L;
    }
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public Long mySalesCancelledCount(Jwt jwt){
        if(jwt!=null){
            AppUser appUser=  appUserDetailsService.getAppUserByUsername(jwt.getSubject());
            SellerProfile seller = sellerProfileService.getSellerProfile(appUser.getId());
            if(seller!=null){
                return  listingOrderItemRepo.countBySellerIdAndListingOrderOrderStatus(seller.getId(),OrderStatus.CANCELLED);
            }
            return 0L;
        }
        return 0L;
    }
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public Long myPurchaseCount(Jwt jwt){
        if(jwt!=null){
            AppUser appUser=  appUserDetailsService.getAppUserByUsername(jwt.getSubject());
            return listingOrderRepo.countByBuyerIdAndOrderStatus(appUser.getId(),OrderStatus.PAID);
        }
        return 0L;
    }
    public ListingOrder getOrderById(Long id){
        return listingOrderRepo.findById(id).orElse(null);
    }
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ListingOrderItem getRecentSaleBySeller(Long sellerId) {
        return listingOrderItemRepo.findFirstBySellerIdAndInventoryItemStatusOrderByCreatedAtDesc(
                sellerId,
                InventoryStatus.SOLD
        ).orElse(null);
    }
}
