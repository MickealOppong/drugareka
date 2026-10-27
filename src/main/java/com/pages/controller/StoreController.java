package com.pages.controller;

import com.pages.dto.*;
import com.pages.service.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    @Autowired
    private ShipmentService shipmentService;

    @Autowired
    private OrderReturnService orderReturnService;
    @Autowired
    private PaymentService paymentService;




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
    public ListPageDto getStoreProductListing( String queryCategory,
                                               @RequestParam(defaultValue = "0") Integer page, Integer size){
        return listingTransactionService.getMarketplaceCatalogFeed(queryCategory,page,size);
    }

    @GetMapping("listing/{listingId}")
    public ListTransResponse getListItem(@RequestParam Long listingId){
        return   listingTransactionService.getProductByListingId(listingId);
    }

    @GetMapping("listing/slug")
    public ListTransResponse getListingItemBySlug(@RequestParam String slug){
        return   listingTransactionService.getProductByListingSlug(slug);
    }

    @GetMapping("listings/seller")
    public List<ListTransResponse> getSellerListings(@RequestParam Long sellerId){
        return   listingTransactionService.getSellerListings(sellerId);
    }

    @GetMapping("/top6-categories")
    public List<CategoryResponse> top6StoreCategories(){
        return categoryService.top6Categories();
    }
    @GetMapping("/top12-categories")
    public List<CategoryResponse> top8StoreCategories(){
        return categoryService.top12Categories();
    }

    @GetMapping("/parent-categories")
    public List<CategoryResponse> allStoreCategories(){
        return categoryService.getAllParentCategories();
    }

    @GetMapping("/categories")
    public List<CategoryResponse> allCategories(){
        return categoryService.getAllCategories();
    }

    @PutMapping("/confirm-delivery")
    public ResponseEntity<Boolean> validateDelivery(@RequestParam("token") String token){
        return ResponseEntity.ok(listingOrderService.validateItemDelivery(token));
    }

    @GetMapping("/verify-confirmation")
    public ResponseEntity<Boolean> isAlreadyConfirmed(@RequestParam("token") String token){
        return ResponseEntity.ok(listingOrderService.isDeliveryConfirmedAlready(token));
    }

    @PutMapping("/confirm-return")
    public ResponseEntity<Boolean> validateReturnDelivery(@RequestParam("token") String token){
       return ResponseEntity.ok(shipmentService.validateAndProcessDelivery(token));
    }

    @GetMapping("/verify-return")
    public ResponseEntity<Boolean> isReturnAlreadyConfirmed(@RequestParam("token") String token){
        return ResponseEntity.ok(shipmentService.isItemReturnConfirmed(token));

    }


    @GetMapping("/category-tree")
    public List<CategoryTreeDto> allStoreCategoriesTree(){
        return categoryService.getCategoryTree();
    }

}
