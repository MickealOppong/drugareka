package com.pages.controller;

import com.pages.dto.*;
import com.pages.exception.PhotoNotFoundException;
import com.pages.service.*;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/item")
public class ProductController {

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

    @PreAuthorize("hasAuthority('ROLE_USER')")
    @PostMapping(value = "/listing",consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseDto<Object> addSellerItem(@AuthenticationPrincipal Jwt jwt, @ModelAttribute @Valid ProductData productData){
        if(productData.getImages()==null){
            throw new PhotoNotFoundException( "Please add product images");
        }
        return listingTransactionService.addSellerProduct(jwt,productData);
    }

    @GetMapping("/products/me")
    public ListPageDto myListings(@AuthenticationPrincipal Jwt jwt,@RequestParam(defaultValue = "1") Integer page,@RequestParam Integer size){
        return listingTransactionService.myCatalogFeed(jwt,page,size);
    }

   // @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @GetMapping("/products")
    public ListPageDto storeListing(@AuthenticationPrincipal Jwt jwt,@RequestParam Integer page,@RequestParam Integer size){
        return listingTransactionService.storeCatalogFeed(jwt,page,size);
    }



    @GetMapping("/categories")
    public List<CategoryResponse> allCategories(){
        return categoryService.getAllCategories();
    }


    @GetMapping("listing/{listingId}")
    public ListTransResponse getListItem(Long listingId){
        return listingTransactionService.getProductByListingId(listingId);
    }

    @GetMapping("/top6-categories")
    public List<CategoryResponse> top6StoreCategories(){
       return categoryService.top6Categories();
    }

    @GetMapping("/all-categories")
    public List<CategoryResponse> allStoreCategories(){

        return categoryService.getAllCategories();
    }


    @PutMapping(value = "/listing/edit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseDto<Object> editListing(@AuthenticationPrincipal Jwt jwt, @ModelAttribute @Valid ProductData dto) {

        if (dto.getImages() == null || dto.getImages().length == 0) {
            throw new PhotoNotFoundException(
                    "Please add product images"
            );
        }

        return listingTransactionService.editSellerProduct(
                jwt,
                dto
        );
    }



}

