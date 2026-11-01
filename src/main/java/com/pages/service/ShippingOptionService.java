package com.pages.service;


import com.pages.dto.CourierResponse;
import com.pages.dto.ShippingOptionRequest;
import com.pages.enums.DeliveryOption;
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
            case "LARGE_UP_30KG" -> ItemSize.LARGE_UP_30KG;
            case "XLARGE_UP_50KG" -> ItemSize.XLARGE_UP_50KG;
            case "HEAVY_OVER_50KG" ->ItemSize.HEAVY_OVER_50KG;
            case"SMALL_UP_10KG"  -> ItemSize.SMALL_UP_10KG;

            default -> throw new IllegalArgumentException(
                    "Unsupported shipping method: " + cleanStatus
            );
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

    public DeliveryOption getDeliveryOption(String method) {
        if (method == null || method.isBlank()) {
            throw new IllegalArgumentException("Shipping method cannot be empty");
        }

        String cleanMethod = method.trim().toUpperCase(Locale.ROOT);

        return switch (cleanMethod) {
            case "PICKUP" -> DeliveryOption.PICKUP;
            case "HOME_COURIER" -> DeliveryOption.HOME_COURIER;
            case "PARCEL_LOCKER" -> DeliveryOption.PARCEL_LOCKER;
            case "PERSONAL_PICKUP" -> DeliveryOption.PERSONAL_PICKUP;
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
                    .buyerOption(item.getDeliveryOption().name())
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
                    .buyerOption(item.getDeliveryOption().name())
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

        DeliveryOption deliveryOption = getDeliveryOption(request.getBuyerOption());

        boolean duplicate = shippingOptionRepo
                .existsByItemSizeAndDeliveryOptionAndShippingMethodAndIdNot(
                        itemSize,
                        deliveryOption,
                        shippingMethod,
                        request.getId()
                );

        if (duplicate) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A shipping option already exists for this item size, delivery option and shipping method."
            );
        }


        shippingOption.setShippingMethod(shippingMethod);
        shippingOption.setItemSize(itemSize);

        shippingOption.setDeliveryOption(deliveryOption);

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

        DeliveryOption deliveryOption = getDeliveryOption(request.getBuyerOption());

        boolean duplicate = shippingOptionRepo
                .existsByItemSizeAndDeliveryOptionAndShippingMethodAndIdNot(
                        itemSize,
                        deliveryOption,
                        shippingMethod,
                        request.getId()
                );

        if (duplicate) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A shipping option already exists for this item size and shipping method."
            );
        }

        ShippingOption shippingOption = ShippingOption.builder()
                .shippingMethod(shippingMethod)
                .itemSize(itemSize)
                .price(request.getPrice())
                .active(request.getActive())
                .deliveryOption(getDeliveryOption(request.getBuyerOption()))
                .discount(request.getDiscount())
                .build();

        shippingOptionRepo.save(shippingOption);
    }




    public BigDecimal getShippingPrice(CartItem cartItem,String deliveryOption) {

        InventoryItem inventoryItem = cartItem.getInventoryItem();

        DeliveryOption option = getDeliveryOption(deliveryOption);

        ShippingOption shippingCost= shippingOptionRepo
                .findByItemSizeAndDeliveryOptionAndActiveTrue(
                        inventoryItem.getItemSize(),option)
                .orElseThrow(() -> new IllegalStateException(
                        "Shipping price not configured for combination size " + inventoryItem.getItemSize()+" and option"
                               +option
                ));
        return shippingCost.getPrice().subtract(shippingCost.getDiscount()).multiply(BigDecimal.valueOf(cartItem.getInventoryItem().getQuantity()));

    }

    public BigDecimal getItemShippingPrice(InventoryItem item,String deliveryOption) {


        DeliveryOption option = getDeliveryOption(deliveryOption);

        ShippingOption shippingCost= shippingOptionRepo
                .findByItemSizeAndDeliveryOptionAndActiveTrue(
                        item.getItemSize(),option)
                .orElseThrow(() -> new IllegalStateException(
                        "Shipping price not configured for combination size " + item.getItemSize()+" and option "
                        +deliveryOption
                ));

        return shippingCost.getPrice().subtract(shippingCost.getDiscount()).multiply(BigDecimal.valueOf(item.getQuantity()));
    }

    private boolean isHeavyOrBulky(ItemSize size) {
        return size == ItemSize.HEAVY_OVER_50KG
                || size == ItemSize.XLARGE_UP_50KG;
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
                        == ItemSize.XLARGE_UP_50KG)
                .count();

        boolean hasMedium = sellerItems.stream()
                .anyMatch(item -> item.getInventoryItem().getItemSize()
                        == ItemSize.LARGE_UP_30KG);

        // Two or more LARGE items
        if (largeCount >= 2) {
            return true;
        }

        // LARGE + MEDIUM
        return largeCount >= 1 && hasMedium;
    }


    public BigDecimal calculateStandardShippingPrice(List<CartItem> items,String option) {

        BigDecimal basePrice = items.stream().map(item->getShippingPrice(item,option))
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

    public BigDecimal calculateSingleShippingPrice(InventoryItem item, String option) {

             return getItemShippingPrice(item,option);

    }

    private boolean isSpecial(ItemSize size) {
        return size == ItemSize.XLARGE_UP_50KG
                || size == ItemSize.HEAVY_OVER_50KG;
    }


}