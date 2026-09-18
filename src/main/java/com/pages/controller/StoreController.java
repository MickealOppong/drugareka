package com.pages.controller;

import com.pages.dto.*;
import com.pages.exception.PhotoNotFoundException;
import com.pages.service.*;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/store")

public class StoreController {

    @Autowired
    private InventoryItemService inventoryItemService;
    @Autowired
    private InventoryItemPriceService inventoryItemPriceService;
    @Autowired
    private ProductCatalogService productCatalogService;

    @Autowired
    private ProductConditionService productConditionService;
    @Autowired
    private BrandService brandService;
    @Autowired
    private MediaService mediaService;
    @Autowired
    private CategoryService categoryService;
    @Autowired
    private AppUserDetailsService appUserDetailsService;
    @Autowired
    private SellerProfileService sellerProfileService;
    @Autowired
    private PricingService pricingService;
    @Autowired
    private ListingTransactionService listingTransactionService;

    @Autowired
    private WishListService wishListService;

    @Autowired
    private ListingOrderService listingOrderService;



    @GetMapping("/listings")
    public List<ListTransResponse>  getListings(@AuthenticationPrincipal Jwt jwt){
           return listingTransactionService.getProductListings();
    }


    @GetMapping("/store-products")
    public List<ListTransResponse> activeStoreListings() {
        return listingTransactionService.getProductListings();
    }

    @GetMapping("/landing-listing")
    public List<ListTransResponse> landingListing() {
        return listingTransactionService.getShopLandingListing();
    }


    @GetMapping("/store-listing")
    public ListPageDto getStoreProductListing( String queryCategory, @RequestParam(defaultValue = "0") Integer page, Integer size){
        return listingTransactionService.getMarketplaceCatalogFeed(queryCategory,page,size);
    }



    @GetMapping("listing/{listingId}")
    public ListTransResponse getListItem(@RequestParam Long listingId){
        return   listingTransactionService.getProductByListingId(listingId);
    }

    @GetMapping("/top6-categories")
    public List<CategoryResponse> top6StoreCategories(){
        return categoryService.top6Categories();
    }

    @GetMapping("/all-categories")
    public List<CategoryResponse> allStoreCategories(){
        return categoryService.getAllCategories();
    }

    @PutMapping("/confirm-delivery")
    public ResponseEntity<Boolean> validateDelivery(@RequestParam("token") String token){
       return ResponseEntity.ok(listingOrderService.validateDelivery(token));
    }

}
