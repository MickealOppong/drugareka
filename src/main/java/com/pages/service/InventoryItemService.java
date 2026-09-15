package com.pages.service;

import com.pages.dto.InventoryItemRequest;
import com.pages.dto.InventoryItemResponse;
import com.pages.enums.InventoryStatus;
import com.pages.enums.ShippingMethod;
import com.pages.model.InventoryItem;
import com.pages.model.ProductCondition;
import com.pages.repository.InventoryItemPriceRepo;
import com.pages.repository.InventoryItemRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class InventoryItemService {

private final InventoryItemRepo inventoryItemRepo;
private final ProductConditionService productConditionService;
private final AppUserDetailsService appUserDetailsService;
private final InventoryItemPriceRepo inventoryItemPriceRepo;


    public InventoryItemService(InventoryItemRepo inventoryItemRepo, ProductConditionService productConditionService,
                                AppUserDetailsService appUserDetailsService, InventoryItemPriceRepo inventoryItemPriceRepo) {
        this.inventoryItemRepo = inventoryItemRepo;
        this.productConditionService = productConditionService;
        this.appUserDetailsService = appUserDetailsService;
        this.inventoryItemPriceRepo = inventoryItemPriceRepo;
    }

    public InventoryItem findAndUpdate(InventoryItemRequest dto){

        ProductCondition savedCondition =productConditionService.findByNameOrCreate(dto.getCondition());

        return null;
    }

    public ShippingMethod getShippingMethod(String method){

        if(method.equals(ShippingMethod.COURIER.name())){
            return ShippingMethod.COURIER;
        }else if(method.equals(ShippingMethod.LOCKER.name())){
            return ShippingMethod.LOCKER;
        }else{
            return null;
        }

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
                    .shippingMethod(getShippingMethod(dto.getShippingMethod()))
                    .productCatalog(dto.getProductCatalog())
                    .sku(dto.getSku())
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
