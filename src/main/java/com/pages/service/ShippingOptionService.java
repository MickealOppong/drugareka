package com.pages.service;


import com.pages.dto.CourierResponse;
import com.pages.dto.ShippingOptionRequest;
import com.pages.enums.ItemSize;
import com.pages.enums.ListingStatus;
import com.pages.enums.ShippingMethod;
import com.pages.model.CartItem;
import com.pages.model.Category;
import com.pages.model.InventoryItem;
import com.pages.repository.CategoryRepo;
import com.pages.repository.ShippingOptionRepo;
import com.pages.util.ShippingOption;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShippingOptionService {

    private final ShippingOptionRepo shippingOptionRepo;

    public ShippingOption getShippingOption(ItemSize itemSize) {

        return shippingOptionRepo
                .findByItemSizeAndActiveTrue(itemSize)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "No active shipping option configured for "
                                        + itemSize));
    }

    public ItemSize getItemSize(String item){
        String cleanStatus = item.trim().toUpperCase();
        return switch (cleanStatus) {
            case "MEDIUM_UP_20KG" -> ItemSize.MEDIUM_UP_20KG;
            case "LARGE_UP_50KG" -> ItemSize.LARGE_UP_50KG;
            case "HEAVY_ABOVE_50KG" ->ItemSize.HEAVY_OVER_50KG;
            default -> ItemSize.SMALL_UP_10KG;
        };
    }

    public ShippingMethod getShippingMethod(String method) {
        if (method == null || method.isBlank()) {
            throw new IllegalArgumentException("Shipping method cannot be empty");
        }

        String cleanMethod = method.trim().toUpperCase(Locale.ROOT);

        return switch (cleanMethod) {
            case "DPD_PICKUP" -> ShippingMethod.DPD_PICKUP;
            case "DPD_COURIER_PICKUP" -> ShippingMethod.DPD_COURIER_PICKUP;
            case "DPD_PICKUP_TO_ADDRESS" -> ShippingMethod.DPD_PICKUP_TO_ADDRESS;
            case "DPD_CLASSIC_COURIER" -> ShippingMethod.DPD_CLASSIC_COURIER;
            case "INPOST_LOCKER" -> ShippingMethod.INPOST_LOCKER;
            case "INPOST_COURIER" -> ShippingMethod.INPOST_COURIER;
            case "OTHER" -> ShippingMethod.OTHER;

            default -> throw new IllegalArgumentException(
                    "Unsupported shipping method: " + cleanMethod
            );
        };
    }


    @Transactional(readOnly = true)
    public CourierResponse getCourier(Jwt jwt,Long id){
        if(jwt==null){
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"You are not authorized to view report");
        }
        return shippingOptionRepo.findById(id).map(item->{
            return CourierResponse.builder()
                    .id(item.getId())
                    .active(item.isActive())
                    .price(item.getPrice())
                    .discount(item.getDiscount())
                    .shippingMethod(item.getShippingMethod())
                    .itemSize(item.getItemSize())
                    .active(item.isActive())
                    .build();
        }).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<CourierResponse> getCourierList(Jwt jwt){
        if(jwt==null){
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"You are not authorized to view report");
        }
        return shippingOptionRepo.findAll().stream().map(item->{
            return CourierResponse.builder()
                    .id(item.getId())
                    .active(item.isActive())
                    .price(item.getPrice())
                    .discount(item.getDiscount())
                    .shippingMethod(item.getShippingMethod())
                    .itemSize(item.getItemSize())
                    .active(item.isActive())
                    .build();
        }).toList();
    }

    public void editShippingOption(Jwt jwt, ShippingOptionRequest request) {

        if (jwt == null) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You are not authorized to edit this record"
            );
        }

        if (request.getId() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Shipping option ID is required for editing"
            );
        }

        ShippingOption shippingOption = shippingOptionRepo.findById(request.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Shipping option not found"
                ));

        ShippingMethod shippingMethod =
                getShippingMethod(request.getShippingMethod());

        ItemSize itemSize =
                getItemSize(request.getItemSize());

        boolean duplicate = shippingOptionRepo
                .existsByItemSizeAndShippingMethodAndIdNot(
                        itemSize,
                        shippingMethod,
                        request.getId()
                );

        if (duplicate) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A shipping option already exists for this item size and shipping method."
            );
        }

        shippingOption.setShippingMethod(shippingMethod);
        shippingOption.setItemSize(itemSize);

        if (request.getPrice() != null) {
            shippingOption.setPrice(request.getPrice());
        }

        if (request.getDiscount() != null) {
            shippingOption.setDiscount(request.getDiscount());
        }

        shippingOption.setActive(request.getActive());

        shippingOptionRepo.save(shippingOption);

        log.info("Shipping option updated. ID: {}", request.getId());
    }


    public void addShippingOption(Jwt jwt, ShippingOptionRequest request) {

        if (jwt == null) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You are not authorized to add a record"
            );
        }

        ShippingMethod shippingMethod =
                getShippingMethod(request.getShippingMethod());

        ItemSize itemSize =
                getItemSize(request.getItemSize());

        boolean duplicate = shippingOptionRepo
                .existsByItemSizeAndShippingMethod(itemSize, shippingMethod);

        if (duplicate) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A shipping option already exists for this item size and shipping method."
            );
        }

        log.info("Shipping option request: {}", request);

        ShippingOption shippingOption = ShippingOption.builder()
                .shippingMethod(shippingMethod)
                .itemSize(itemSize)
                .price(request.getPrice())
                .active(request.getActive())
                .discount(request.getDiscount())
                .build();

        shippingOptionRepo.save(shippingOption);
    }



    public BigDecimal getShippingPrice(CartItem cartItem,String selectedMethod) {

        InventoryItem inventoryItem = cartItem.getInventoryItem();

        ShippingMethod shippingMethod = getShippingMethod(selectedMethod);

        ShippingOption shippingCost= shippingOptionRepo
                .findByItemSizeAndShippingMethodAndActiveTrue(
                        inventoryItem.getItemSize(),shippingMethod)
                .orElseThrow(() -> new IllegalStateException(
                        "Shipping price not configured for combination size " + inventoryItem.getItemSize()+" and method "
                               +shippingMethod
                ));
        return shippingCost.getPrice().subtract(shippingCost.getDiscount());

    }

    public BigDecimal getItemShippingPrice(InventoryItem item,String selectedMethod) {


        ShippingMethod shippingMethod = getShippingMethod(selectedMethod);

        ShippingOption shippingCost= shippingOptionRepo
                .findByItemSizeAndShippingMethodAndActiveTrue(
                        item.getItemSize(),shippingMethod)
                .orElseThrow(() -> new IllegalStateException(
                        "Shipping price not configured for combination size " + item.getItemSize()+" and method "
                                +shippingMethod
                ));

        return shippingCost.getPrice().subtract(shippingCost.getDiscount());
    }

    private boolean isHeavyOrBulky(ItemSize size) {
        return size == ItemSize.HEAVY_OVER_50KG
                || size == ItemSize.LARGE_UP_50KG;
    }

    private boolean isSpecialCart(List<CartItem> sellerItems) {

        boolean hasHeavyOrBulky = sellerItems.stream()
                .anyMatch(item -> isHeavyOrBulky(
                        item.getInventoryItem().getItemSize()));

        if (hasHeavyOrBulky) {
            return true;
        }

        long largeCount = sellerItems.stream()
                .filter(item -> item.getInventoryItem().getItemSize()
                        == ItemSize.LARGE_UP_50KG)
                .count();

        boolean hasMedium = sellerItems.stream()
                .anyMatch(item -> item.getInventoryItem().getItemSize()
                        == ItemSize.MEDIUM_UP_20KG);

        // Two or more LARGE items
        if (largeCount >= 2) {
            return true;
        }

        // LARGE + MEDIUM
        return largeCount >= 1 && hasMedium;
    }


    public BigDecimal calculateStandardShippingPrice(List<CartItem> items,String method) {

        BigDecimal basePrice = items.stream().map(item->getShippingPrice(item,method))
                .max(BigDecimal::compareTo)
                        .orElse(BigDecimal.ZERO);

        int itemCount = items.size();

        if (itemCount <= 1) {
            return basePrice;
        }


      boolean isSpecial=  isSpecialCart(items);

        BigDecimal multiplier =isSpecial? BigDecimal.ONE.add(
                BigDecimal.valueOf(0.50)
                        .multiply(BigDecimal.valueOf(itemCount - 2))
        ):BigDecimal.ONE.add(
                BigDecimal.valueOf(0.0)
                        .multiply(BigDecimal.valueOf(itemCount - 2))
        );

        return basePrice.multiply(multiplier);

    }

    public BigDecimal calculateSingleShippingPrice(InventoryItem item,String method) {

             return getItemShippingPrice(item,method);

    }

    private boolean isSpecial(ItemSize size) {
        return size == ItemSize.LARGE_UP_50KG
                || size == ItemSize.HEAVY_OVER_50KG;
    }


}