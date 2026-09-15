package com.pages.controller;

import com.pages.dto.InventoryItemResponse;
import com.pages.model.InventoryItem;
import com.pages.service.AppUserDetailsService;
import com.pages.service.InventoryItemPriceService;
import com.pages.service.InventoryItemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/inventories")
public class InventoryItemController {


    private final InventoryItemPriceService inventoryItemPriceService;
    private final InventoryItemService inventoryItemService;
    private final AppUserDetailsService appUserDetailsService;

    public InventoryItemController(InventoryItemPriceService inventoryItemPriceService, InventoryItemService inventoryItemService, AppUserDetailsService appUserDetailsService) {
        this.inventoryItemPriceService = inventoryItemPriceService;
        this.inventoryItemService = inventoryItemService;
        this.appUserDetailsService = appUserDetailsService;
    }

    @GetMapping("/seller")
    public List<InventoryItemResponse> getInventoryBySeller(@AuthenticationPrincipal Jwt jwt) {
        String username = jwt.getSubject();
        Long sellerId = appUserDetailsService.getAppUserByUsername(username).getId();
          return   inventoryItemService.getAllInventoryBySeller(sellerId);
    }
}
