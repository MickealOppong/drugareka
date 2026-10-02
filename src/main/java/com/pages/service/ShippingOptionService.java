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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShippingOptionService {

    private final ShippingOptionRepo shippingOptionRepo;
    private final CategoryRepo categoryRepo;

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
            case "MEDIUM" -> ItemSize.MEDIUM_UP_20KG;
            case "LARGE" -> ItemSize.LARGE_UP_50KG;
            case "HEAVY" ->ItemSize.HEAVY_OVER_50KG;
            default -> ItemSize.SMALL_UP_10KG;
        };
    }

    private ShippingMethod getShippingMethod(String method) {
        String cleanStatus = method.trim().toUpperCase();
        return switch (cleanStatus) {
            case "INPOST" ->ShippingMethod.INPOST_COURIER;
            case "DPD" -> ShippingMethod.DPD;
            default -> ShippingMethod.OTHER;
        };

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
                    .shippingMethod(item.getShippingMethod())
                    .itemSize(item.getItemSize())
                    .active(item.isActive())
                    .build();
        }).toList();
    }

    public void addOrEditShippingOption(Jwt jwt, ShippingOptionRequest request){
        if(jwt==null){
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"You are not authorized to add record");
        }
     ShippingOption shippingOption=   shippingOptionRepo.findByItemSizeAndShippingMethod(getItemSize(request.getItemSize())
                     ,getShippingMethod(request.getShippingMethod()))
             .orElse(null);

        if(shippingOption!=null){
            if(StringUtils.hasText(request.getShippingMethod())){
                shippingOption.setShippingMethod(getShippingMethod(request.getShippingMethod()));
            }
            if(StringUtils.hasText(request.getItemSize())){
                shippingOption.setItemSize(getItemSize(request.getItemSize()));
            }
            if(request.getPrice()!=null){
                shippingOption.setPrice(request.getPrice());
            }
            shippingOption.setActive(request.getActive());
            shippingOptionRepo.save(shippingOption);

        }else{

            ShippingOption newOption= ShippingOption.builder()
                    .shippingMethod(getShippingMethod(request.getShippingMethod()))
                    .active(request.getActive())
                    .price(request.getPrice())
                    .itemSize(getItemSize(request.getItemSize()))
                    .build();

            shippingOptionRepo.save(newOption);
        }
    }




   public BigDecimal getShippingPrice(CartItem cartItem) {
        InventoryItem inventoryItem = cartItem.getInventoryItem();

        return shippingOptionRepo
                .findByItemSizeAndShippingMethodAndActiveTrue(
                        inventoryItem.getItemSize(),ShippingMethod.DPD).map(ShippingOption::getPrice)
                .orElseThrow(() -> new IllegalStateException(
                        "Shipping price not configured for "
                                + inventoryItem.getItemSize()
                ));

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


    public BigDecimal calculateStandardShippingPrice(List<CartItem> items) {

        BigDecimal basePrice = items.stream().map(this::getShippingPrice)
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

    private boolean isSpecial(ItemSize size) {
        return size == ItemSize.LARGE_UP_50KG
                || size == ItemSize.HEAVY_OVER_50KG;
    }


}