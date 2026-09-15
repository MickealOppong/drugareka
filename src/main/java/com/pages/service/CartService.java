package com.pages.service;

import com.pages.dto.*;
import com.pages.enums.CartStatus;
import com.pages.enums.InventoryStatus;
import com.pages.enums.ListingStatus;
import com.pages.model.*;
import com.pages.repository.CartItemRepo;
import com.pages.repository.CartRepo;
import com.pages.repository.ListingTransactionRepo;
import com.pages.util.ShippingProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CartService {

    private final CartRepo cartRepository;
    private final CartItemRepo cartItemRepository;
    private final ListingTransactionRepo transactionRepository;
    private final AppUserDetailsService appUserDetailsService;
    private final MediaService mediaService;
    private final ShippingProperties shippingProperties;
    private final InventoryItemService inventoryItemService;
    private final InventoryItemPriceService inventoryItemPriceService;
    private final GlobalAddressService globalAddressService;

    private static final Duration RESERVATION_WINDOW = Duration.ofMinutes(15);

    /**
     * RETRIEVE CURRENT ACTIVE BUYER CART LOCK
     */

    private Cart getOrCreateActiveCart(AppUser buyer) {
        return cartRepository.findByBuyerIdAndStatus(buyer.getId(),CartStatus.ACTIVE)
                .orElseGet(() -> {
                    log.info("Initializing fresh primary shopping cart ledger for Buyer Username: {}", buyer.getUsername());
                    Cart newCart = Cart.builder()
                            .buyer(buyer)
                            .status(CartStatus.ACTIVE)
                            .build();
                    return cartRepository.save(newCart);
                });
    }


    /**
     * ADD ELEMENT ROW TO CART
     */
    @Transactional
    public ResponseDto<Object> addItemToCart(Long[] listingIds, Jwt jwt) {

        if (jwt == null) {
            return ResponseDto.builder()
                    .data(false)
                    .message("Authentication required")
                    .httpStatus(HttpStatus.UNAUTHORIZED.value())
                    .build();
        }

        if (listingIds == null || listingIds.length == 0) {
            return ResponseDto.builder()
                    .data(false)
                    .message("No items selected")
                    .httpStatus(HttpStatus.BAD_REQUEST.value())
                    .build();
        }

        try {
            // 1. Get buyer context fields safely
            String username = jwt.getSubject();
            AppUser buyer = appUserDetailsService.getAppUserByUsername(username);

            // 2. Retrieve or initialize the active persistent cart container row
            Cart cart = getOrCreateActiveCart(buyer);
            Instant reservedUntil = Instant.now().plus(RESERVATION_WINDOW);

            int addedCount = 0;
            int updatedCount = 0;

            // ==========================================================================
            //  FIX 1: USE THE ENHANCED FOR-EACH LOOP FOR TRUE DB PRIMARY KEYS
            // ==========================================================================
            for (Long targetListingId : listingIds) {
                if (targetListingId == null) continue;

                // Find active listing records matching safety states
                ListingTransaction listing = transactionRepository
                        .findByIdAndListingStatus(targetListingId, ListingStatus.PUBLISHED)
                        .orElse(null);

                if (listing == null || listing.getInventory() == null) {
                    continue; // Skip invalid, sold, or deleted listings gracefully
                }

                InventoryItem inventoryItem = listing.getInventory();
                log.info("item {}",inventoryItem);


                // Safely checks your object chain: inventory -> seller -> id
                if (inventoryItem.getSeller() != null &&
                        inventoryItem.getSeller().getUser().getId().equals(buyer.getId())) {
                    log.warn("User {} attempted to buy their own listing {}", buyer.getId(), targetListingId);
                    continue; // Protect platform integrity and skip
                }

                Long inventoryItemId = inventoryItem.getId();

                // 3. Extract the active store pricing tier matrix records
                InventoryItemPriceResponse price = inventoryItemPriceService.getPrices(inventoryItemId).toPriceDto();

                // 4. Evaluate whether item already exists in the database user cart
                Optional<CartItem> existingItem = cartItemRepository
                        .findByCartIdAndListingId(cart.getId(), targetListingId);

                if (existingItem.isPresent()) {
                    CartItem cartItem = existingItem.get();
                    cartItem.setPrice(price.getStoreNewPrice());
                    cartItem.setReservedUntil(reservedUntil);

                    cartItemRepository.save(cartItem);
                    updatedCount++;
                } else {
                    // 5. Build and persist a fresh line entry
                    CartItem cartItem = CartItem.builder()
                            .cart(cart)
                            .inventoryItemId(inventoryItemId)
                            .listingId(targetListingId)
                            .price(price.getStoreNewPrice())
                            .shippingMethod(inventoryItem.getShippingMethod())
                            .reservedUntil(reservedUntil)
                            .build();

                    cartItemRepository.save(cartItem);
                    addedCount++;

                    log.info("Added listing ID {} to buyer cart ID {}", targetListingId, cart.getId());
                }
            }

            // 6. Evaluate outcome matrix to structure a response payload
            if (addedCount == 0 && updatedCount == 0) {
                return ResponseDto.builder()
                        .data(false)
                        .message("No available items were added to the cart")
                        .httpStatus(HttpStatus.NOT_FOUND.value())
                        .build();
            }

            return ResponseDto.builder()
                    .data(true)
                    .message(String.format("Cart updated. Added: %d, Updated: %d", addedCount, updatedCount))
                    .httpStatus(HttpStatus.OK.value())
                    .build();

        } catch (Exception e) {
            log.error("Failed to execute bulk cart append operations for user", e);
            return ResponseDto.builder()
                    .data(false)
                    .message("Unable to update cart records")
                    .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
                    .build();
        }
    }



    /**
     * REMOVE SPECIFIC LISTING ROW FROM CART
     */
    @Transactional
    public void removeItemFromCart(Long listingId, Jwt jwt) {

        String username = jwt.getSubject();
        AppUser buyer = appUserDetailsService.getAppUserByUsername(username);


        Cart cart = cartRepository.findByBuyerIdAndStatus(buyer.getId(),CartStatus.ACTIVE)
                .orElseThrow(() -> new IllegalArgumentException("Active cart session not found."));

        cartItemRepository.findByCartIdAndListingId(cart.getId(), listingId)
                .ifPresent(item -> {
                    log.info("Removing Item ID: {} from Cart ID: {}", listingId, cart.getId());
                    cartItemRepository.delete(item);
                });

        //update inventory status if reserved by this buyer
        ListingTransaction listingTransaction = transactionRepository.findById(listingId).orElse(null);
        if(listingTransaction != null){
           InventoryItem inventoryItem = listingTransaction.getInventory();
           if(inventoryItem != null){
               Long inventoryId = inventoryItem
                       .getId();
               inventoryItemService.releaseBuyerReservedInventoryItem(inventoryId);
           }

        }


        //delete cart header if all children are deleted
        List<CartItem> remainder = cartItemRepository.findAllByCartBuyerId(buyer.getId());
        if(remainder.isEmpty()){
            cartRepository.delete(cart);
        }
    }

    /**
     * MANUAL FORCE SWEEP CLEAR ON SUCCESSFUL CHECKOUT FLOW EXECUTION
     */
    @Transactional
    public void clearCartOnCheckoutSuccess(AppUser buyer) {
        cartRepository.findByBuyerIdAndStatus(buyer.getId(),CartStatus.ACTIVE).ifPresent(cart -> {
            log.info("Purging all active tracking items for successfully closed Cart ID: {}", cart.getId());
            cartItemRepository.deleteAllByCartId(cart.getId());
            cart.setStatus(CartStatus.CLOSED); // Drop active state indicator flag tags
            cartRepository.save(cart);
        });
    }

    public Long getCartCount(Jwt jwt){
        if(jwt ==null){
                return 0L;
        }
        Long userId = appUserDetailsService.getAppUserByUsername(jwt.getSubject()).getId();
      return cartItemRepository.countItemsByCartBuyerId(userId);
    }



    @Transactional(readOnly = true)
    public CartResponse getCart(Jwt jwt) {

        if(jwt !=null){

            AppUser buyer = appUserDetailsService.getAppUserByUsername(jwt.getSubject());

            Cart cart = cartRepository.findByBuyerIdAndStatus(buyer.getId(),CartStatus.ACTIVE).orElse(null);
            if(cart==null){
                return null;
            }
            // Retrieve buyer delivery address credentials if they exist
            AddressResponse address = globalAddressService.getAddress(buyer);

            // Destination collection array deck to pass back to React
            List<CartItemDto> flatCartItemDtoList = new ArrayList<>();

            // 1. Fetch all raw active cart item rows belonging to this buyer session
            List<CartItem> rawCartItems = cartItemRepository.findAllByCartBuyerId(buyer.getId());
            if (rawCartItems.isEmpty()) {
                return CartResponse.builder()
                        .cartId(cart.getId())
                        .status(cart.getStatus())
                        .address(address)
                        .cartItemList(flatCartItemDtoList)
                        .build();
            }

            // ==========================================================================
            //  STEP 2: GROUP THE RAW DATABASE ENTRIES BY THEIR SELLER ID
            // ==========================================================================
            Map<Long, List<CartItem>> itemsGroupedBySeller = rawCartItems.stream()
                    .collect(Collectors.groupingBy(cartItem -> {
                        ListingTransaction listing = transactionRepository.findById(cartItem.getListingId())
                                .orElseThrow(() -> new IllegalArgumentException("Listing records not found for ID: " + cartItem.getListingId()));
                        return listing.getInventory().getSeller().getId();
                    }));


           // BigDecimal singleFlatShippingRate = shippingProperties.getSingleFlatShippingCost(); // e.g., 9.99 zl
           // BigDecimal groupFlatShippingRate = shippingProperties.getGroupFlatShippingCost(); // e.g., 12.99 zl

            // ==========================================================================
            //  STEP 3: LOOP THROUGH GROUPS AND DISTRIBUTE CONDITIONAL SHIPPING COSTS
            // ==========================================================================
            for (Map.Entry<Long, List<CartItem>> sellerGroup : itemsGroupedBySeller.entrySet()) {
                List<CartItem> bundledItems = sellerGroup.getValue();
                boolean isBundle = bundledItems.size() > 1; // Flag to check if it's a multi-item package

                for (int i = 0; i < bundledItems.size(); i++) {
                    CartItem cartItem = bundledItems.get(i);

                    // Fetch asset information once
                    MediaResponse media = mediaService.getListingMainImage(cartItem.getInventoryItemId());
                   InventoryItem inventoryItem= inventoryItemService.getInventoryItem(cartItem.getInventoryItemId(), InventoryStatus.AVAILABLE);
                   String productName = inventoryItem!=null?inventoryItem.getProductCatalog().getName():null;

                    // CLEAN CONDITIONAL ASSIGNMENT:
                    // If it's not the first item, shipping is ALWAYS zero.
                    // If it IS the first item, we choose between the single rate or group rate based on our flag!
                    BigDecimal assignedItemShipping =BigDecimal.ZERO;

                    if (i == 0) {
                        assignedItemShipping = isBundle ?shippingProperties.getGroupFlatShippingCost()
                                : shippingProperties.getSingleFlatShippingCost();
                    }

                    CartItemDto dto = CartItemDto.builder()
                            .price(cartItem.getPrice())
                            .listingId(cartItem.getListingId())
                            .cartItemId(cartItem.getId())
                            .inventoryId(cartItem.getInventoryItemId())
                            .shipping(assignedItemShipping)
                            .shippingMethod(cartItem.getShippingMethod().name())
                            .image(media.getImage())
                            .productName(productName)
                            .reservedUntil(cartItem.getReservedUntil())
                            .build();

                    flatCartItemDtoList.add(dto);
        }

            }
            // Return unified platform payload mapping metrics directly
            return CartResponse.builder()
                    .cartId(cart.getId())
                    .status(cart.getStatus())
                    .address(address)
                    .buyerId(cart.getBuyer().getId())
                    .cartItemList(flatCartItemDtoList)
                    .build();
        }

        return CartResponse.builder()
                .cartItemList(new ArrayList<>())
                .address(null)
                .cartId(null)
                .build();

    }


    public List<CartItem> getCartItems(Long buyerId){
       return cartItemRepository.findAllByCartBuyerId(buyerId);
    }
    public void deleteCart(AppUser buyer){
        cartRepository.findByBuyerIdAndStatus(buyer.getId(),CartStatus.ACTIVE).ifPresent(cart->{
           cartItemRepository.deleteAllByCartId(cart.getId());
            cartRepository.delete(cart);
        });

    }

    public void deleteCartById(Long buyer){
      Cart cart=  cartRepository.findByBuyerIdAndStatus(buyer,CartStatus.ACTIVE).orElse(null);
      if(cart !=null){
          cartItemRepository.deleteAllByCartId(cart.getId());
          cartRepository.delete(cart);
      }

    }
}
