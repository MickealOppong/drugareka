package com.pages.service;

import com.pages.dto.*;
import com.pages.enums.CartStatus;
import com.pages.enums.InventoryStatus;
import com.pages.enums.ListingStatus;
import com.pages.model.*;
import com.pages.repository.CartItemRepo;
import com.pages.repository.CartRepo;
import com.pages.repository.ListingTransactionRepo;
import com.pages.repository.SellerProfileRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
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
    private final InventoryItemService inventoryItemService;
    private final InventoryItemPriceService inventoryItemPriceService;
    private final GlobalAddressService globalAddressService;
    private final ShippingOptionService shippingOptionService;
    private final SellerProfileRepo sellerProfileRepo;
    private final MessageSource messageSource;
    private final PricingService pricingService;

    private static final Duration RESERVATION_WINDOW = Duration.ofMinutes(15);

    /**
     * RETRIEVE CURRENT ACTIVE BUYER CART LOCK
     */
    private Cart getOrCreateActiveCart(AppUser buyer) {
        return cartRepository.findByBuyerIdAndStatus(buyer.getId(), CartStatus.ACTIVE)
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
     * Merges the client-side localStorage guest cart items into the permanent database user cart upon login.
     */
    @Transactional
    public ResponseDto<Object> mergeGuestCart(Jwt jwt, Long[] guestListingIds) {
        Locale currentLocale = LocaleContextHolder.getLocale();

        // 1. Structural Security Validation Guard
        if (jwt == null) {
            String msg = messageSource.getMessage("cart.auth.required.merge", null, "Authentication is required to merge carts.", currentLocale);
            return ResponseDto.builder()
                    .data(false)
                    .message(msg)
                    .httpStatus(HttpStatus.UNAUTHORIZED.value())
                    .build();
        }

        // 2. Empty Payload Handler
        if (guestListingIds == null || guestListingIds.length == 0) {
            String msg = messageSource.getMessage("cart.guest.empty", null, "There are no guest cart items to merge.", currentLocale);
            return ResponseDto.builder()
                    .data(true)
                    .message(msg)
                    .httpStatus(HttpStatus.OK.value())
                    .build();
        }

        try {
            log.info("Executing kasoa.pl Cart Merge Protocol for buyer: {}. Total item tracks: {}",
                    jwt.getSubject(), guestListingIds.length);

            ResponseDto<Object> mergeResult = addItemToCart(guestListingIds, jwt);

            // 3. Evaluate outcomes to return descriptive localized status payloads
            if (mergeResult.getHttpStatus() == HttpStatus.OK.value()) {
                String msg = messageSource.getMessage("cart.merge.success", null, "Guest cart items have been successfully merged into your account.", currentLocale);
                return ResponseDto.builder()
                        .data(true)
                        .message(msg)
                        .httpStatus(HttpStatus.OK.value())
                        .build();
            }

            if (mergeResult.getHttpStatus() == HttpStatus.NOT_FOUND.value()) {
                String msg = messageSource.getMessage("cart.merge.items_unavailable", null, "The guest cart items are no longer available.", currentLocale);
                return ResponseDto.builder()
                        .data(true) // Return true so frontend clears the local storage anyway
                        .message(msg)
                        .httpStatus(HttpStatus.OK.value())
                        .build();
            }

            return mergeResult;

        } catch (Exception e) {
            log.error("Fatal exception intercepted during database cart merge protocol execution", e);
            String msg = messageSource.getMessage("cart.merge.failed", null, "Unable to merge the guest cart into your account.", currentLocale);
            return ResponseDto.builder()
                    .data(false)
                    .message(msg)
                    .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
                    .build();
        }
    }

    /**
     * ADD ELEMENT ROW TO CART
     */
    @Transactional
    public ResponseDto<Object> addItemToCart(Long[] listingIds, Jwt jwt) {
        Locale currentLocale = LocaleContextHolder.getLocale();

        if (jwt == null) {
            String msg = messageSource.getMessage("cart.auth.required", null, "Authentication is required.", currentLocale);
            return ResponseDto.builder()
                    .data(false)
                    .message(msg)
                    .httpStatus(HttpStatus.UNAUTHORIZED.value())
                    .build();
        }

        if (listingIds == null || listingIds.length == 0) {
            String msg = messageSource.getMessage("cart.items.none_selected", null, "No items were selected.", currentLocale);
            return ResponseDto.builder()
                    .data(false)
                    .message(msg)
                    .httpStatus(HttpStatus.BAD_REQUEST.value())
                    .build();
        }

        try {
            String username = jwt.getSubject();
            AppUser buyer = appUserDetailsService.getAppUserByUsername(username);

            Cart cart = getOrCreateActiveCart(buyer);
            Instant reservedUntil = Instant.now().plus(RESERVATION_WINDOW);

            int addedCount = 0;
            int updatedCount = 0;

            for (Long targetListingId : listingIds) {
                if (targetListingId == null) continue;

                ListingTransaction listing = transactionRepository
                        .findByIdAndListingStatus(targetListingId, ListingStatus.PUBLISHED)
                        .orElse(null);

                if (listing == null || listing.getInventory() == null) {
                    continue;
                }

                InventoryItem inventoryItem = listing.getInventory();

                if (inventoryItem.getReservedBy() != null && !inventoryItem.getReservedBy().equals(buyer.getId()) && inventoryItem.getReservedUntil().isAfter(Instant.now())) {
                    String msg = messageSource.getMessage("cart.item.reserved", null, "This item is currently reserved by another buyer.", currentLocale);
                    return ResponseDto.builder()
                            .data(false)
                            .message(msg)
                            .httpStatus(HttpStatus.BAD_REQUEST.value())
                            .build();
                }

                if (inventoryItem.getSeller() != null && inventoryItem.getSeller().getUser().getId().equals(buyer.getId())) {
                    log.warn("User {} attempted to buy their own listing {}", buyer.getId(), targetListingId);
                    continue;
                }

                InventoryItemPrice price = inventoryItemPriceService.getPrices(inventoryItem.getId());

                Optional<CartItem> existingItem = cartItemRepository
                        .findByCartIdAndListingId(cart.getId(), targetListingId);

                if (existingItem.isPresent()) {
                    CartItem cartItem = existingItem.get();
                    cartItem.setPrice(price.getSellerNewPrice());
                    cartItem.setServiceCharge(price.getNewServiceCharge());
                    cartItem.setReservedUntil(reservedUntil);
                    cartItemRepository.save(cartItem);
                    updatedCount++;
                } else {
                    CartItem cartItem = CartItem.builder()
                            .cart(cart)
                            .inventoryItem(inventoryItem)
                            .listingId(targetListingId)
                            .price(price.getSellerNewPrice())
                            .serviceCharge(price.getNewServiceCharge())
                            .reservedUntil(reservedUntil)
                            .build();

                    cartItemRepository.save(cartItem);
                    addedCount++;

                    log.info("Added listing ID {} to buyer cart ID {}", targetListingId, cart.getId());
                }
            }

            if (addedCount == 0 && updatedCount == 0) {
                String msg = messageSource.getMessage("cart.items.none_added", null, "No available items were added to your cart.", currentLocale);
                return ResponseDto.builder()
                        .data(false)
                        .message(msg)
                        .httpStatus(HttpStatus.NOT_FOUND.value())
                        .build();
            }

//  Safe passing of dynamic sequential counts {0} and {1} straight to bundle properties arrays
            Object[] messageParams = new Object[]{addedCount, updatedCount};
            String successMsg = messageSource.getMessage("cart.update.success", messageParams, "Cart updated.", currentLocale);
            return ResponseDto.builder()
                    .data(true)
                    .message(successMsg)
                    .httpStatus(HttpStatus.OK.value())
                    .build();
        } catch (Exception e) {
            log.error("Failed to execute bulk cart append operations for user", e);
            String msg = messageSource.getMessage("cart.update.failed", null, "Unable to update your cart.", currentLocale);
            return ResponseDto.builder()
                    .data(false)
                    .message(msg)
                    .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
                    .build();
        }
    }


    /**
     * ADD ELEMENT ROW TO CART AT BUYER OFFER PRICE
     */
    @Transactional
    public ResponseDto<Object> addItemToCartBuyerOffer(Long[] listingIds, Jwt jwt,BigDecimal offer) {
        Locale currentLocale = LocaleContextHolder.getLocale();

        if (jwt == null) {
            String msg = messageSource.getMessage("cart.auth.required", null, "Authentication is required.", currentLocale);
            return ResponseDto.builder()
                    .data(false)
                    .message(msg)
                    .httpStatus(HttpStatus.UNAUTHORIZED.value())
                    .build();
        }

        if (listingIds == null || offer==null) {
            String msg = messageSource.getMessage("cart.items.none_selected", null, "No items were selected.", currentLocale);
            return ResponseDto.builder()
                    .data(false)
                    .message(msg)
                    .httpStatus(HttpStatus.BAD_REQUEST.value())
                    .build();
        }

        try {
            String username = jwt.getSubject();
            AppUser buyer = appUserDetailsService.getAppUserByUsername(username);

            Cart cart = getOrCreateActiveCart(buyer);
            Instant reservedUntil = Instant.now().plus(RESERVATION_WINDOW);

            int addedCount = 0;
            int updatedCount = 0;

            for (Long targetListingId : listingIds) {
                if (targetListingId == null) continue;

                ListingTransaction listing = transactionRepository
                        .findByIdAndListingStatus(targetListingId, ListingStatus.PUBLISHED)
                        .orElse(null);

                if (listing == null || listing.getInventory() == null) {
                    continue;
                }

                InventoryItem inventoryItem = listing.getInventory();

                if (inventoryItem.getReservedBy() != null && !inventoryItem.getReservedBy().equals(buyer.getId()) && inventoryItem.getReservedUntil().isAfter(Instant.now())) {
                    String msg = messageSource.getMessage("cart.item.reserved", null, "This item is currently reserved by another buyer.", currentLocale);
                    return ResponseDto.builder()
                            .data(false)
                            .message(msg)
                            .httpStatus(HttpStatus.BAD_REQUEST.value())
                            .build();
                }

                if (inventoryItem.getSeller() != null && inventoryItem.getSeller().getUser().getId().equals(buyer.getId())) {
                    log.warn("User {} attempted to buy their own listing {}", buyer.getId(), targetListingId);
                    continue;
                }

                InventoryItemPriceResponse price = inventoryItemPriceService.getPrices(inventoryItem.getId()).toPriceDto();

                Optional<CartItem> existingItem = cartItemRepository
                        .findByCartIdAndListingId(cart.getId(), targetListingId);

                if (existingItem.isPresent()) {
                    CartItem cartItem = existingItem.get();
                    cartItem.setPrice(offer);
                    cartItem.setServiceCharge(pricingService.calculateServiceCharge(offer));
                    cartItem.setReservedUntil(reservedUntil);
                    cartItemRepository.save(cartItem);
                    updatedCount++;
                } else {
                    CartItem cartItem = CartItem.builder()
                            .cart(cart)
                            .inventoryItem(inventoryItem)
                            .listingId(targetListingId)
                            .price(offer)
                            .serviceCharge(pricingService.calculateServiceCharge(offer))
                            .reservedUntil(reservedUntil)
                            .build();

                    cartItemRepository.save(cartItem);
                    addedCount++;

                    log.info("Added listing ID {} to buyer cart ID {}", targetListingId, cart.getId());
                }
            }

            if (addedCount == 0 && updatedCount == 0) {
                String msg = messageSource.getMessage("cart.items.none_added", null, "No available items were added to your cart.", currentLocale);
                return ResponseDto.builder()
                        .data(false)
                        .message(msg)
                        .httpStatus(HttpStatus.NOT_FOUND.value())
                        .build();
            }

//Safe passing of dynamic sequential counts {0} and {1} straight to bundle properties arrays
            Object[] messageParams = new Object[]{addedCount, updatedCount};
            String successMsg = messageSource.getMessage("cart.update.success", messageParams, "Cart updated.", currentLocale);
            return ResponseDto.builder()
                    .data(true)
                    .message(successMsg)
                    .httpStatus(HttpStatus.OK.value())
                    .build();
        } catch (Exception e) {
            log.error("Failed to execute bulk cart append operations for user", e);
            String msg = messageSource.getMessage("cart.update.failed", null, "Unable to update your cart.", currentLocale);
            return ResponseDto.builder()
                    .data(false)
                    .message(msg)
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
        Cart cart = cartRepository.findByBuyerIdAndStatus(buyer.getId(), CartStatus.ACTIVE)
                .orElseThrow(() -> new IllegalArgumentException(
                        messageSource.getMessage("cart.active.not_found", null, "No active cart was found.", LocaleContextHolder.getLocale())
                ));
        cartItemRepository.findByCartIdAndListingId(cart.getId(), listingId)
                .ifPresent(item -> {
                    log.info("Removing Item ID: {} from Cart ID: {}", listingId, cart.getId());
                    cartItemRepository.delete(item);
                });
        ListingTransaction listingTransaction = transactionRepository.findById(listingId).orElse(null);
        if (listingTransaction != null) {
            InventoryItem inventoryItem = listingTransaction.getInventory();
            if (inventoryItem != null) {
                Long inventoryId = inventoryItem.getId();
                inventoryItemService.releaseBuyerReservedInventoryItem(inventoryId);
            }
        }
        List<CartItem> remainder = cartItemRepository.findAllByCartBuyerId(buyer.getId());
        if (remainder.isEmpty()) {
            cartRepository.delete(cart);
        }
    }
    /**
     * MANUAL FORCE SWEEP CLEAR ON SUCCESSFUL CHECKOUT FLOW EXECUTION
     */
    @Transactional
    public void clearCartOnCheckoutSuccess(AppUser buyer) {
        cartRepository.findByBuyerIdAndStatus(buyer.getId(), CartStatus.ACTIVE).ifPresent(cart -> {
            log.info("Purging all active tracking items for successfully closed Cart ID: {}", cart.getId());
            cartItemRepository.deleteAllByCartId(cart.getId());
            cart.setStatus(CartStatus.CLOSED);
            cartRepository.save(cart);
        });
    }
    public Long getCartCount(Jwt jwt) {
        if (jwt == null) {
            return 0L;
        }
        Long userId = appUserDetailsService.getAppUserByUsername(jwt.getSubject()).getId();
        return cartItemRepository.countItemsByCartBuyerId(userId);
    }
    @Transactional(readOnly = true)
    public CartResponse getCart(Jwt jwt) {
        if (jwt != null) {
            AppUser buyer = appUserDetailsService.getAppUserByUsername(jwt.getSubject());
            Cart cart = cartRepository.findByBuyerIdAndStatus(buyer.getId(), CartStatus.ACTIVE).orElse(null);
            if (cart == null) {
                return null;
            }
            AddressResponse address = globalAddressService.getAddress(buyer);
            List<CartItemDto> flatCartItemDtoList = new ArrayList<>();
            List<CartItem> rawCartItems = cartItemRepository.findAllByCartBuyerId(buyer.getId());
            if (rawCartItems.isEmpty()) {
                return CartResponse.builder()
                        .cartId(cart.getId())
                        .status(cart.getStatus())
                        .address(address)
                        .cartItemList(flatCartItemDtoList)
                        .build();
            }
            Map<Long, List<CartItem>> itemsGroupedBySeller = rawCartItems.stream()
                    .collect(Collectors.groupingBy(cartItem -> {
                        ListingTransaction listing = transactionRepository.findById(cartItem.getListingId())
                                .orElseThrow(() -> new IllegalArgumentException(
                                        messageSource.getMessage("cart.listing.not_found", new Object[]{cartItem.getListingId()}, "Listing not found.", LocaleContextHolder.getLocale())
                                ));
                        return listing.getInventory().getSeller().getId();
                    }));
            for (Map.Entry<Long, List<CartItem>> sellerGroup : itemsGroupedBySeller.entrySet()) {
                List<CartItem> bundledItems = sellerGroup.getValue();
                for (int i = 0; i < bundledItems.size(); i++) {
                    CartItem cartItem = bundledItems.get(i);
                    MediaResponse media = mediaService.getListingMainImage(cartItem.getInventoryItem().getId());
                    InventoryItem inventoryItem = inventoryItemService.getInventoryItem(cartItem.getInventoryItem().getId(), InventoryStatus.AVAILABLE);
                    String productName = inventoryItem != null ? inventoryItem.getProductCatalog().getName() : null;
                    BigDecimal assignedItemShipping = BigDecimal.ZERO;
                    if (i == 0) {
                        assignedItemShipping = shippingOptionService.calculateStandardShippingPrice(sellerGroup.getValue());
                    }
                    CartItemDto dto = CartItemDto.builder()
                            .price(cartItem.getPrice())
                            .listingId(cartItem.getListingId())
                            .cartItemId(cartItem.getId())
                            .inventoryId(cartItem.getInventoryItem().getId())
                            .shipping(assignedItemShipping)
                            .image(media != null ? media.getImage() : null)
                            .productName(productName)
                            .serviceCharge(cartItem.getServiceCharge())
                            .reservedUntil(cartItem.getReservedUntil())
                            .sellerId(cartItem.getInventoryItem().getSeller().getId())
                            .build();
                    flatCartItemDtoList.add(dto);
                }
            }
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
    public List<CartItem> getCartItems(Long buyerId) {
        return cartItemRepository.findAllByCartBuyerId(buyerId);
    }
    public void deleteCart(AppUser buyer) {
        cartRepository.findByBuyerIdAndStatus(buyer.getId(), CartStatus.ACTIVE).ifPresent(cart -> {
            cartItemRepository.deleteAllByCartId(cart.getId());
            cartRepository.delete(cart);
        });
    }
    @Transactional
    public void deleteCartById(Long buyer) {
        Cart cart = cartRepository.findByBuyerIdAndStatus(buyer, CartStatus.ACTIVE).orElse(null);
        if (cart != null) {
            cartItemRepository.deleteAllByCartId(cart.getId());
            cartRepository.delete(cart);
        }
    }
}