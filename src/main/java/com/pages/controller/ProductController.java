package com.pages.controller;

import com.pages.dto.*;
import com.pages.exception.PhotoNotFoundException;
import com.pages.model.Category;
import com.pages.service.*;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

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
    @Autowired
    private MessageSource messageSource;

    @PreAuthorize("hasAuthority('ROLE_USER')")
    @PostMapping(value = "/listing",consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseDto<Object> addSellerItem(@AuthenticationPrincipal Jwt jwt, @ModelAttribute @Valid ProductData productData){
        if(productData.getImages()==null){
            throw new PhotoNotFoundException( "api.add_listing.product.image.error");
        }


        // 1. Tokenize path string safely into localized lowercase segments
        List<String> categories = Arrays.stream(productData.getCategoryPath().split("/"))
                .map(String::toLowerCase)
                .toList();

        // 2. Existing Dimension Rule Evaluation
        Set<String> dimensionKeys = Set.of("automotive", "furniture", "home-garden", "travel-luggage", "sports-recreation");
        boolean requiresDimensions = categories.stream().anyMatch(dimensionKeys::contains);

        boolean missingDimensions = !StringUtils.hasText(productData.getHeight())
                || !StringUtils.hasText(productData.getWidth())
                || !StringUtils.hasText(productData.getDepth());

        if (requiresDimensions && missingDimensions) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageSource.getMessage("api.add_listing.product.error", null, LocaleContextHolder.getLocale()));
        }

        // 3. NEW AUTOMOTIVE OEM/ITEM NUMBER MANDATORY RULE
        boolean isAutomotive = categories.contains("automotive");
        boolean missingItemNumber = !StringUtils.hasText(productData.getItemNumber());

        if (isAutomotive && missingItemNumber) {
            // Throws a Bad Request exception if a user attempts to save an automotive part without an OEM number string
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageSource.getMessage("api.add_listing.automotive.item_number.error", null, LocaleContextHolder.getLocale()));
        }
log.info("dTA:{}",productData);
        return listingTransactionService.addSellerProduct(jwt,productData);
    }

    @PostMapping("/make-offer")
    public ResponseDto<Object> buyerOffer(@AuthenticationPrincipal Jwt jwt, Long listingId, BigDecimal offer){
        return listingTransactionService.validateBuyerOffer(jwt,listingId,offer);
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


    @PreAuthorize("hasAuthority('ROLE_ADMIN') || hasAuthority('ROLE_SELLER')")
    @PutMapping(value = "/listing/edit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseDto<Object> editListing(@AuthenticationPrincipal Jwt jwt, @ModelAttribute @Valid ProductData productData) {
        if(productData.getImages()==null){
            throw new PhotoNotFoundException( "api.add_listing.product.image.error");
        }

        // 1. Tokenize path string safely into localized lowercase segments
        List<String> categories = Arrays.stream(productData.getCategoryPath().split("/"))
                .map(String::toLowerCase)
                .toList();

        // 2. Existing Dimension Rule Evaluation
        Set<String> dimensionKeys = Set.of("automotive", "furniture", "home-garden", "travel-luggage", "sports-recreation");
        boolean requiresDimensions = categories.stream().anyMatch(dimensionKeys::contains);

        boolean missingDimensions = !StringUtils.hasText(productData.getHeight())
                || !StringUtils.hasText(productData.getWidth())
                || !StringUtils.hasText(productData.getDepth());

        if (requiresDimensions && missingDimensions) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageSource.getMessage("api.add_listing.product.error", null, LocaleContextHolder.getLocale()));
        }

        // 3. NEW AUTOMOTIVE OEM/ITEM NUMBER MANDATORY RULE
        boolean isAutomotive = categories.contains("automotive");
        boolean missingItemNumber = !StringUtils.hasText(productData.getItemNumber());

        if (isAutomotive && missingItemNumber) {
            // Throws a Bad Request exception if a user attempts to save an automotive part without an OEM number string
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    messageSource.getMessage("api.add_listing.automotive.item_number.error", null, LocaleContextHolder.getLocale()));
        }
        return listingTransactionService.editSellerProduct(jwt, productData);
    }

    @DeleteMapping("/listing/delete")
    public void deleteListing(@AuthenticationPrincipal Jwt jwt,Long listingId){
        listingTransactionService.deleteListing(jwt,listingId);
    }


}

