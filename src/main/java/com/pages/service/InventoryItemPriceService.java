package com.pages.service;

import com.pages.dto.InventoryItemPriceDto;
import com.pages.dto.ResponseDto;
import com.pages.model.InventoryItem;
import com.pages.model.InventoryItemPrice;
import com.pages.repository.InventoryItemPriceRepo;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class InventoryItemPriceService {

    private final InventoryItemPriceRepo inventoryItemPriceRepo;

    public InventoryItemPriceService(InventoryItemPriceRepo inventoryItemPriceRepo) {
        this.inventoryItemPriceRepo = inventoryItemPriceRepo;
    }

    public InventoryItemPrice addNewPrice(InventoryItemPriceDto  dto){
           InventoryItemPrice newPrice = new InventoryItemPrice(dto);
           newPrice.setInventoryItem(dto.getInventoryItem());
        return inventoryItemPriceRepo.save(newPrice);
    }

    public void deleteInventoryPrice(InventoryItem item){
        inventoryItemPriceRepo.deleteByInventoryItem(item);
    }

    public void savePrice(InventoryItemPrice price){
        inventoryItemPriceRepo.save(price);
    }


    public InventoryItemPrice getPrices(Long inventoryId){
        return inventoryItemPriceRepo.findByInventoryItemId(inventoryId).orElse(null);
    }


}
