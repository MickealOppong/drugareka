package com.pages.service;

import com.pages.dto.*;
import com.pages.enums.*;
import com.pages.model.*;
import com.pages.repository.*;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


@Slf4j
@Service
public class ListingOrderService {


    private final ListingOrderRepo listingOrderRepo;

    private final ListingOrderItemRepo listingOrderItemRepo;


    private final AppUserDetailsService appUserDetailsService;

    private final CartService cartService;
    private final PaymentService paymentService;


    private final InventoryItemRepo inventoryItemRepo;
    private final InventoryItemPriceService inventoryItemPriceService;

    private final SellerProfileService sellerProfileService;
    private final SellerShipmentRepo sellerShipmentRepo;

    public ListingOrderService(ListingOrderRepo listingOrderRepo, ListingOrderItemRepo listingOrderItemRepo, AppUserDetailsService appUserDetailsService, CartService cartService, @Lazy PaymentService paymentService, InventoryItemRepo inventoryItemRepo, InventoryItemPriceService inventoryItemPriceService, SellerProfileService sellerProfileService, SellerShipmentRepo sellerShipmentRepo) {
        this.listingOrderRepo = listingOrderRepo;
        this.listingOrderItemRepo = listingOrderItemRepo;
        this.appUserDetailsService = appUserDetailsService;
        this.cartService = cartService;
        this.paymentService = paymentService;
        this.inventoryItemRepo = inventoryItemRepo;
        this.inventoryItemPriceService = inventoryItemPriceService;
        this.sellerProfileService = sellerProfileService;
        this.sellerShipmentRepo = sellerShipmentRepo;
    }


    @Transactional
    public ListingOrder createBuyerOrder(Jwt jwt) {
        if (jwt == null) {
            throw new IllegalArgumentException("Could not authenticate buyer");
        }

        CartResponse cart = cartService.getCart(jwt);

        log.info("Creating order from cart {}", cart);
        if (cart == null || cart.getCartItemList() == null || cart.getCartItemList().isEmpty()) {
            throw new IllegalStateException("Cart is empty");
        }

        if (cart.getAddress() == null) {
            throw new IllegalStateException("Shipping address is required");
        }

        String permanentShippingAddress = String.join(
                ", ", cart.getAddress().street().trim(),
                cart.getAddress().postalCode().trim(),cart.getAddress().city().trim(),
                cart.getAddress().country().trim(),cart.getAddress().contact()
        );

        // 1. Generate a distinct transaction tracking reference identifier string
        String orderNumber = "DR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        BigDecimal subTotal = cart.getCartItemList().stream()
                .map(CartItemDto::getPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        AppUser buyer = appUserDetailsService.getAppUserId(cart.getBuyerId());

        // 2. Build and save the Initial Order Header Container Anchor Instance
        ListingOrder listingOrder = ListingOrder.builder()
                .orderNumber(orderNumber)
                .buyer(buyer)
                .buyerNameSnapshot(buyer.getFirstName()+" "+buyer.getLastName())
                .shippingAddress(permanentShippingAddress)
                .totalShipmentCost(BigDecimal.ZERO)
                .orderTotal(subTotal)
                .currency("PLN")
                .subTotal(subTotal)
                .orderStatus(OrderStatus.PROCESSING)
                .build();

        ListingOrder orderHeader = listingOrderRepo.save(listingOrder);

        BigDecimal totalShippingCost = BigDecimal.ZERO;
        BigDecimal subtotalAccumulator = BigDecimal.ZERO;

        for (CartItemDto cartItem : cart.getCartItemList()) {
            // Hold explicit row-level database locks to completely eliminate overselling or racing
            InventoryItem inventoryItem = inventoryItemRepo.findByIdForUpdate(cartItem.getInventoryId())
                    .orElseThrow(() -> new IllegalArgumentException("Product not found: " + cartItem.getInventoryId()));

            // Null-safe reservation checks preventing NullPointerException crashes
            if (inventoryItem.getStatus() != InventoryStatus.AVAILABLE &&
                    !orderHeader.getBuyer().getId().equals(inventoryItem.getReservedBy())) {
                throw new IllegalStateException("Product is no longer available for purchase: " + inventoryItem.getId());
            }

            // 3. Update inventory item lifecycle parameters to prevent item stealing from feed grids
            inventoryItem.setStatus(InventoryStatus.RESERVED);
            inventoryItem.setReservedBy(orderHeader.getBuyer().getId());
            inventoryItem.setReservedUntil(Instant.now().plus(15, ChronoUnit.MINUTES));
            inventoryItemRepo.save(inventoryItem);

            // Accumulate mathematical parameters
            BigDecimal itemPrice = cartItem.getPrice();
            BigDecimal shipping = cartItem.getShipping();

            subtotalAccumulator = subtotalAccumulator.add(itemPrice);
            totalShippingCost = totalShippingCost.add(shipping);

            log.info("Compiling order item for seller User ID: {}", inventoryItem.getSeller().getUser().getId());

            // Extract historical seller records safely
            InventoryItemPrice sellerPrice = inventoryItemPriceService.getPrices(inventoryItem.getId());
            BigDecimal sellerItemPrice = (sellerPrice != null) ? sellerPrice.getSellerNewPrice() : BigDecimal.ZERO;

            //seller name
            String sellerName = inventoryItem.getSeller().getUser().getFirstName()+" "+inventoryItem.getSeller().getUser().getLastName();
            // 4. Build child item model instance with safe parent back-reference links
            ListingOrderItem orderItem = ListingOrderItem.builder()
                    .listingOrder(orderHeader)
                    .listingId(cartItem.getListingId())
                    .seller(inventoryItem.getSeller())
                    .finalizedPrice(itemPrice.add(shipping))
                    .subtotal(itemPrice)
                    .sellerNameSnapshot(sellerName)
                    .shippingMethod(ShippingMethod.DPD)
                    .productBrandSnapshot(inventoryItem.getProductCatalog().getBrand().getName())
                    .productConditionSnapshot(inventoryItem.getProductCondition().getName())
                    .productNameSnapshot(inventoryItem.getProductCatalog().getName())
                    .sellerPrice(sellerItemPrice)
                    .inventoryItem(inventoryItem)
                    .shippingCost(shipping)
                    .orderItemStatus(OrderItemStatus.PROCESSING) // Synchronize state controllers cleanly
                    .build();

            // 5. Dual Bidirectional registration link updates
            orderHeader.addItem(orderItem);
        }

        // 6. Update single parent aggregate figures cleanly
        orderHeader.setSubTotal(subtotalAccumulator);
        orderHeader.setTotalShipmentCost(totalShippingCost);
        orderHeader.setOrderTotal(subtotalAccumulator.add(totalShippingCost));
        orderHeader.setOrderStatus(OrderStatus.PROCESSING);

        // 7. Flush the completely unified object graph in a single atomic database pass!
        return listingOrderRepo.save(orderHeader);
    }



    public boolean validateDelivery(String token) {

        ListingOrderItem orderItem =
                listingOrderItemRepo
                        .findByReceiptConfirmationToken(token)
                        .orElse(null);

        if (orderItem == null) {
            return false;
        }

        Instant now = Instant.now();

        // Token expired
        if (orderItem.getReceiptConfirmationTokenExpiresAt() == null
                || orderItem.getReceiptConfirmationTokenExpiresAt().isBefore(now)) {
            return false;
        }

        // Already confirmed
        if (orderItem.getReceiptConfirmedAt() != null) {
            return false;
        }

        // Confirm receipt
        orderItem.setReceiptConfirmedAt(now);

        // Invalidate token
        orderItem.setReceiptConfirmationTokenExpiresAt(now);

        listingOrderItemRepo.save(orderItem);

        return true;
    }

    public void save(ListingOrderItem listingOrderItem){
        listingOrderItemRepo.save(listingOrderItem);
    }
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    private OrderPageDto getStoreOrders(Jwt jwt, int page , int size){
        if(jwt!=null){
            AppUser buyer = appUserDetailsService.getAppUserByUsername(jwt.getSubject());

            // 2. Check if the current context authorities stream contains the Admin credential string
            List<String> roles = jwt.getClaimAsStringList("ROLE");

            boolean isAdmin = roles.contains("ROLE_ADMIN");
            if(isAdmin){

                Pageable pageable = PageRequest.of(
                        page-1,
                        size,
                        Sort.by(Sort.Direction.DESC, "createdAt")
                );


             Page<OrderDto> orders= listingOrderItemRepo.findAll(pageable).map(order->{

                 //buyer name
                 String buyerName =appUserDetailsService.getUsernameById(order.getListingOrder().getBuyer().getId());

                 String sellerName =appUserDetailsService.getUsernameById(order.getSeller().getUser().getId());

                 return OrderDto.builder()
                         .id(order.getListingOrder().getId())
                         .orderNumber(order.getListingOrder().getOrderNumber())
                         .orderTotal(order.getListingOrder().getOrderTotal())
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


                    return OrderDto.builder()
                            .id(order.getId())
                            .orderNumber(order.getOrderNumber())
                            .orderTotal(order.getSubTotal())
                            .shipping(order.getTotalShipmentCost())
                            .orderStatus(order.getOrderStatus())
                            .currency(order.getCurrency())
                            .createdAt(order.getCreatedAt())
                            .seller("Store")
                            .buyer(order.getBuyerNameSnapshot())
                            .paidAt(order.getPaidAt())
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
                        .orderTotal(orderItem.getFinalizedPrice())
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
                        .orderTotal(orderItem.getSellerPrice())
                        .shipping(orderItem.getShippingCost())
                        .orderItemStatus(orderItem.getOrderItemStatus())
                        .currency(orderItem.getListingOrder().getCurrency())
                        .createdAt(orderItem.getCreatedAt())
                        .seller(orderItem.getSellerNameSnapshot())
                        .buyer("Store")
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
                            .orderTotal(item.getSubtotal())
                            .shipping(item.getShippingCost())
                            .orderStatus(order.getOrderStatus())
                            .currency(order.getCurrency())
                            .createdAt(item.getCreatedAt())
                          .trackingNumber(trackingNumber)
                          .deliveryStatus(shipmentStatus)
                          .orderItemStatus(item.getOrderItemStatus())
                          .deliveryUpdatedAt(updatedAt)
                            .seller("Store")
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

        // Pass the explicit SOLD enum context to gather matching transactions
        return listingOrderItemRepo.findFirstBySellerIdAndInventoryItemStatusOrderByCreatedAtDesc(
                sellerId,
                InventoryStatus.SOLD
        ).orElse(null);
    }



}
