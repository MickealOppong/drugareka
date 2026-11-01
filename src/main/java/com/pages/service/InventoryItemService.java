package com.pages.service;

import com.pages.dto.InventoryItemRequest;
import com.pages.dto.InventoryItemResponse;
import com.pages.enums.InventoryStatus;
import com.pages.enums.ItemSize;
import com.pages.enums.ShippingMethod;
import com.pages.model.InventoryItem;
import com.pages.model.ProductCondition;
import com.pages.repository.InventoryItemRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Slf4j
@Service
public class InventoryItemService {

private final InventoryItemRepo inventoryItemRepo;
private final ProductConditionService productConditionService;
private final AppUserDetailsService appUserDetailsService;


    public InventoryItemService(InventoryItemRepo inventoryItemRepo, ProductConditionService productConditionService,
                                AppUserDetailsService appUserDetailsService) {
        this.inventoryItemRepo = inventoryItemRepo;
        this.productConditionService = productConditionService;
        this.appUserDetailsService = appUserDetailsService;
    }

    public InventoryItem findAndUpdate(InventoryItemRequest dto){

        ProductCondition savedCondition =productConditionService.findByNameOrCreate(dto.getCondition());

        return null;
    }
    public void deleteInventory(InventoryItem item){
        inventoryItemRepo.delete(item);
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

            default -> throw new IllegalArgumentException(
                    "Unsupported shipping method: " + cleanMethod
            );
        };
    }

    public ItemSize getItemDimension(String size){

       return switch (size.toLowerCase()) {
           case "medium_up_20kg" -> ItemSize.MEDIUM_UP_20KG;
           case "large_up_30kg" -> ItemSize.LARGE_UP_30KG;
           case "xlarge_up_50kg" -> ItemSize.XLARGE_UP_50KG;
           case "heavy_above_50kg" -> ItemSize.HEAVY_OVER_50KG;
           case "small_up_10kg" -> ItemSize.SMALL_UP_10KG;
           default -> throw new IllegalArgumentException(
                   "Unsupported shipping method: " + size
           );
       };

    }

    public InventoryItem addNewInventory(InventoryItemRequest dto){
        /*
                CONDITION
         */
            ProductCondition savedCondition =productConditionService.findByNameOrCreate(dto.getCondition());

              /*
               ACTUAL INVENTORY
         */

            InventoryItem newInventory = InventoryItem.builder()
                    .seller(dto.getSeller())
                    .status(dto.getStatus())
                    .deliveryInfo(dto.getShippingInfo())
                    .productCatalog(dto.getProductCatalog())
                    .sku(dto.getSku())
                    .depth(dto.getDepth())
                    .width(dto.getWidth())
                    .height(dto.getHeight())
                    .quantity(dto.getQuantity())
                    .productionYear(dto.getProductionYear())
                    .model(dto.getModel())
                    .personalCollectionAvailable(dto.isPersonalCollectionAvailable())
                    .collectionCity(dto.getCollectionCity())
                    .itemNumber(dto.getItemNumber())
                    .itemSize(getItemDimension(dto.getItemSize()))
                    .productCondition(savedCondition)
                    .build();
            appUserDetailsService.addSellerRole(dto.getSeller().getUser());
          return inventoryItemRepo.save(newInventory);
    }


    public List<InventoryItemResponse> getAllInventoryBySeller(Long sellerId) {
        return inventoryItemRepo.findBySeller(sellerId).stream().map(inventoryItem -> {
            return InventoryItemResponse.builder()
                    .seller(inventoryItem.getSeller().getId())
                    .id(inventoryItem.getId())
                    .status(inventoryItem.getStatus())
                    .condition(inventoryItem.getProductCondition())
                    .productCatalog(inventoryItem.getProductCatalog())
                    .build();
        }).toList();
    }

    public List<InventoryItemResponse> getAllInventoryByStatus(String status){
        return inventoryItemRepo.findByStatus(status).stream().map(inventoryItem -> {
            return InventoryItemResponse.builder()
                    .seller(inventoryItem.getSeller().getId())
                    .condition(inventoryItem.getProductCondition())
                    .id(inventoryItem.getId())
                    .status(inventoryItem.getStatus())
                    .productCatalog(inventoryItem.getProductCatalog())
                    .build();
        }).toList();
    }

    public InventoryItem getInventoryItem(Long id, InventoryStatus inventoryStatus){
       return inventoryItemRepo.findByIdAndStatus(id,inventoryStatus)
                .orElse(null);
    }

    public void releaseBuyerReservedInventoryItem(Long inventoryId){
       InventoryItem inventoryItem =inventoryItemRepo.findById(inventoryId).orElse(null);
       if(inventoryItem != null){
           inventoryItem.setStatus(InventoryStatus.AVAILABLE);
           inventoryItemRepo.save(inventoryItem);
       }
    }

    public void save(InventoryItem inventoryItem) {
        inventoryItemRepo.save(inventoryItem);
    }
}
