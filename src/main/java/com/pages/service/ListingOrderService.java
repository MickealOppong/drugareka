package com.pages.service;

import com.pages.dto.*;
import com.pages.enums.InventoryStatus;
import com.pages.enums.OrderStatus;
import com.pages.enums.ShipmentStatus;
import com.pages.exception.EntityNotFoundException;
import com.pages.model.*;
import com.pages.repository.*;
import com.pages.util.ShippingProperties;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;


@Slf4j
@Service
public class ListingOrderService {


    private final ListingOrderRepo listingOrderRepo;

    private final ListingOrderItemRepo listingOrderItemRepo;


    private final AppUserDetailsService appUserDetailsService;

    private final CartService cartService;


    private final InventoryItemRepo inventoryItemRepo;
    private final InventoryItemPriceService inventoryItemPriceService;

    private final SellerProfileService sellerProfileService;
    private final SellerShipmentRepo sellerShipmentRepo;

    public ListingOrderService(ListingOrderRepo listingOrderRepo, ListingOrderItemRepo listingOrderItemRepo, AppUserDetailsService appUserDetailsService, CartService cartService, InventoryItemRepo inventoryItemRepo, InventoryItemPriceService inventoryItemPriceService, SellerProfileService sellerProfileService, SellerShipmentRepo sellerShipmentRepo) {
        this.listingOrderRepo = listingOrderRepo;
        this.listingOrderItemRepo = listingOrderItemRepo;
        this.appUserDetailsService = appUserDetailsService;
        this.cartService = cartService;
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

        log.info("Creating order from cart {}",cart);
        if (cart == null || cart.getCartItemList() == null || cart.getCartItemList().isEmpty()) {
            throw new IllegalStateException("Cart is empty");
        }

      //  AppUser buyer = appUserDetailsService.getAppUserByUsername(jwt.getSubject());

        if (cart.getAddress() == null) {
            throw new IllegalStateException("Shipping address is required");
        }

        String permanentShippingAddress = String.join(
                ", ", cart.getAddress().street().trim(),
                cart.getAddress().postalCode().trim() + " " + cart.getAddress().city().trim(),
                cart.getAddress().country().trim()
        );

        // 1. Generate a distinct, cryptographic public tracking reference string (Receipt)
        String orderNumber = "DR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        //2. calculate total cart value
        BigDecimal subTotal =cart.getCartItemList().stream().map(CartItemDto::getPrice)
                .reduce(BigDecimal.ZERO,BigDecimal::add);


        // 1. Build or retrieve the Order Header Instance
        ListingOrder listingOrder= ListingOrder.builder()
                .orderNumber(orderNumber)
                .buyerId(cart.getBuyerId())
                .shippingAddress(permanentShippingAddress)
                .totalShipmentCost(BigDecimal.ZERO)
                .orderTotal(subTotal)
                .currency("PLN")
                .subTotal(subTotal)
                .orderStatus(OrderStatus.PROCESSING)
                .build();

       ListingOrder orderHeader = listingOrderRepo.save(listingOrder);

        BigDecimal totalShippingCost = BigDecimal.ZERO;
        BigDecimal subtotal = BigDecimal.ZERO;


        for (CartItemDto cartItem : cart.getCartItemList()) {
            // Hold row-level lock to prevent concurrency overselling
            InventoryItem inventoryItem = inventoryItemRepo.findByIdForUpdate(cartItem.getInventoryId())
                    .orElseThrow(() -> new IllegalArgumentException("Product not found: " + cartItem.getInventoryId()));

            // Concurrency Guard Check
            if (inventoryItem.getStatus() != InventoryStatus.AVAILABLE && !inventoryItem.getReservedBy().equals(listingOrder.getBuyerId())) {
                throw new IllegalStateException("Product is no longer available for purchase: " + inventoryItem.getId());
            }

            // 2. FIXED: Lock item state to RESERVED so no one else can steal it from the feed
            inventoryItem.setStatus(InventoryStatus.RESERVED);
            inventoryItem.setReservedBy(orderHeader.getBuyerId());
            inventoryItem.setReservedUntil(Instant.now().plus(15, ChronoUnit.MINUTES));
            inventoryItemRepo.save(inventoryItem);

            // Accumulate mathematical totals
            BigDecimal itemPrice = cartItem.getPrice();
            BigDecimal shipping = cartItem.getShipping();

            subtotal = subtotal.add(itemPrice);
            totalShippingCost = totalShippingCost.add(shipping);

            log.info("Compiling order item for seller ID: {}", inventoryItem.getSeller().getUser().getId());

            // 3. Build child model item instance safely
            ListingOrderItem orderItem = ListingOrderItem.builder()
                    .listingId(cartItem.getListingId())
                    .seller(inventoryItem.getSeller())
                    .shippingMethod(inventoryItem.getShippingMethod())
                    .finalizedPrice(itemPrice.add(shipping))
                    .subtotal(itemPrice)
                    .inventoryItem(inventoryItem)
                    .shippingCost(shipping)
                    .build();

            // 4. CRITICAL BIDIRECTIONAL HANDSHAKE: updates child reference AND adds to parent list collection
            orderHeader.addItem(orderItem);
        }

        // 5. Apply the final calculated numbers onto your single order header entity
        orderHeader.setSubTotal(subtotal);
        orderHeader.setTotalShipmentCost(totalShippingCost);
        orderHeader.setOrderTotal(subtotal.add(totalShippingCost));
        orderHeader.setOrderStatus(OrderStatus.PROCESSING); // Initialize state control

        // 6. SINGLE FLUSH PERSISTENCE: CascadeType.ALL will automatically save
        // all your mapped ListingOrderItems along with their assigned auto-increment IDs!
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
                 String buyerName =appUserDetailsService.getUsernameById(order.getListingOrder().getBuyerId());

                 String sellerName =appUserDetailsService.getUsernameById(order.getSeller().getUser().getId());

                 return OrderDto.builder()
                         .id(order.getListingOrder().getId())
                         .orderNumber(order.getListingOrder().getOrderNumber())
                         .orderTotal(order.getListingOrder().getOrderTotal())
                         .orderStatus(order.getListingOrder().getOrderStatus())
                         .paidAt(order.getListingOrder().getPaidAt())
                         .buyer(buyerName)
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
                            .buyer(appUserDetailsService.getUsernameById(order.getBuyerId()))
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

                String buyer = appUserDetailsService.getUsernameById(orderItem.getListingOrder().getBuyerId());

                return OrderDto.builder()
                        .id(orderItem.getListingOrder().getId())
                        .orderNumber(orderItem.getListingOrder().getOrderNumber())
                        .orderTotal(orderItem.getFinalizedPrice())
                        .shipping(orderItem.getShippingCost())
                        .orderStatus(orderItem.getListingOrder().getOrderStatus())
                        .currency(orderItem.getListingOrder().getCurrency())
                        .createdAt(orderItem.getCreatedAt())
                        .seller(orderItem.getSeller().getUser().getFirstName()+" "+orderItem.getSeller().getUser().getLastName())
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

            InventoryItemPrice price= inventoryItemPriceService.getPrices(orderItem.getInventoryItem().getId());

                return OrderDto.builder()
                        .id(orderItem.getListingOrder().getId())
                        .orderNumber(orderItem.getListingOrder().getOrderNumber())
                        .orderTotal(price.getSellerNewPrice())
                        .shipping(orderItem.getShippingCost())
                        .orderStatus(orderItem.getListingOrder().getOrderStatus())
                        .currency(orderItem.getListingOrder().getCurrency())
                        .createdAt(orderItem.getCreatedAt())
                        .seller(orderItem.getSeller().getUser().getFirstName()+" "+orderItem.getSeller().getUser().getLastName())
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

                String user = appUserDetailsService.getUsernameById(order.getBuyerId());

                for(ListingOrderItem item :order.getItems()){

               SellerShipment shipment= sellerShipmentRepo.findByListingOrderItemId(item.getId()).orElse(null);
               String trackingNumber  =shipment!=null?shipment.getTrackingNumber():null;
               String deliveryStatus = shipment!=null?shipment.getShipmentStatus().name():null;

                  OrderDto dto= OrderDto.builder()
                            .id(item.getId())
                            .orderNumber(order.getOrderNumber())
                            .orderTotal(item.getSubtotal())
                            .shipping(item.getShippingCost())
                            .orderStatus(order.getOrderStatus())
                            .currency(order.getCurrency())
                            .createdAt(item.getCreatedAt())
                          .trackingNumber(trackingNumber)
                          .deliveryStatus(deliveryStatus)
                            .seller("Store")
                            .buyer(user)
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
