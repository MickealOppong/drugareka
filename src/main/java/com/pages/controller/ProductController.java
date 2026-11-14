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
import java.util.*;

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

    private static final Set<String> DIMENSION_REQUIRED_CATEGORIES = Set.of(
            "automotive",
            "furniture",
            "home-garden",
            "travel-luggage",
            "sports-recreation"
    );

    private static final Set<String> MODEL_REQUIRED_CATEGORIES = Set.of(
            "automotive",
            "electronics",
            "home-appliances",
            "tools"
    );

    private static final Set<String> ITEM_NUMBER_REQUIRED_CATEGORIES = Set.of(
            "automotive-parts",
            "electronics",
            "home-appliances"
    );


    @PreAuthorize("hasAuthority('ROLE_USER')")
    @PostMapping(value = "/listing",consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseDto<Object> addSellerItem(
            @AuthenticationPrincipal Jwt jwt,
            @ModelAttribute @Valid ProductData productData) {

        // 1. Validate product images
        if (productData.getImages() == null
                || productData.getImages().length==0) {

            throw new PhotoNotFoundException(
                    "api.add_listing.product.image.error"
            );
        }

        // 2. Safely extract category path
        List<String> categories = productData.getCategoryPath() == null
                ? Collections.emptyList()
                : Arrays.stream(productData.getCategoryPath().split("/"))
                .map(String::trim)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .filter(StringUtils::hasText)
                .toList();

        // 3. Determine category requirements
        boolean requiresDimensions = categories.stream()
                .anyMatch(DIMENSION_REQUIRED_CATEGORIES::contains);

        boolean requiresModel = categories.stream()
                .anyMatch(MODEL_REQUIRED_CATEGORIES::contains);

        boolean requiresItemNumber = categories.stream()
                .anyMatch(ITEM_NUMBER_REQUIRED_CATEGORIES::contains);

        // 4. Validate dimensions
        boolean missingDimensions =
                !StringUtils.hasText(productData.getHeight())
                        || !StringUtils.hasText(productData.getWidth())
                        || !StringUtils.hasText(productData.getDepth());

        if (requiresDimensions && missingDimensions) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    messageSource.getMessage(
                            "api.add_listing.product.error",
                            null,
                            LocaleContextHolder.getLocale()
                    )
            );
        }

        // 5. Validate model
        if (requiresModel && !StringUtils.hasText(productData.getModel())) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    messageSource.getMessage(
                            "api.add_listing.product.model.error",
                            null,
                            LocaleContextHolder.getLocale()
                    )
            );
        }

        // 6. Validate OEM / part / product number
        if (requiresItemNumber
                && !StringUtils.hasText(productData.getItemNumber())) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    messageSource.getMessage(
                            "api.add_listing.automotive.item_number.error",
                            null,
                            LocaleContextHolder.getLocale()
                    )
            );
        }

        // 7. Validate personal collection city
        if (productData.isPersonalCollectionAvailable()
                && !StringUtils.hasText(productData.getCollectionCity())) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    messageSource.getMessage(
                            "api.add_listing.city.error",
                            null,
                            LocaleContextHolder.getLocale()
                    )
            );
        }

        // 8. Save listing
        return listingTransactionService.addSellerProduct(jwt, productData);
    }


    @PostMapping("/make-offer")
    public ResponseDto<Object> buyerOffer(@AuthenticationPrincipal Jwt jwt, Long listingId, BigDecimal offer){
        return listingTransactionService.validateBuyerOffer(jwt,listingId,offer);
    }

    @GetMapping("/products/me")
    public ListPageDto myListings(@AuthenticationPrincipal Jwt jwt,@RequestParam(defaultValue = "1") Integer page
            ,@RequestParam Integer size,@RequestParam("query")String query){

        return listingTransactionService.myCatalogFeed(jwt,query,page,size);
    }

   // @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @GetMapping("/products")
    public ListPageDto storeListing(@AuthenticationPrincipal Jwt jwt,@RequestParam Integer page,@RequestParam Integer size){
        return listingTransactionService.storeCatalogFeed(jwt,page,size);
    }






    @GetMapping("/categories")
    public List<CategoryResponse> allCategories(@RequestParam("page") int page,@RequestParam("size") int size,@RequestParam("query") String query){
        return categoryService.getAllCategoriesByPage(query,page,size);
    }


    @GetMapping("listing/{listingId}")
    public ProductListResponse getListItem(@AuthenticationPrincipal Jwt jwt
                                           ,Long listingId){
        return listingTransactionService.getProductById(jwt,listingId);
    }

    @GetMapping("/top6-categories")
    public List<CategoryResponse> top6StoreCategories(){
       return categoryService.top6Categories();
    }

    @GetMapping("/all-categories")
    public List<CategoryResponse> allStoreCategories(){

        return categoryService.getAllCategories();
    }


    //@PreAuthorize("hasAuthority('ROLE_ADMIN') || hasAuthority('ROLE_SELLER')")
    @PutMapping(value = "/listing/edit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseDto<Object> editListing(
            @AuthenticationPrincipal Jwt jwt,
            @ModelAttribute @Valid ProductData productData) {

        if (productData.getImages() == null || productData.getImages().length==0) {
            throw new PhotoNotFoundException(
                    "api.add_listing.product.image.error"
            );
        }

        // Tokenize category path safely
        List<String> categories = productData.getCategoryPath() == null
                ? Collections.emptyList()
                : Arrays.stream(productData.getCategoryPath().split("/"))
                .map(category -> category.trim().toLowerCase(Locale.ROOT))
                .filter(StringUtils::hasText)
                .toList();

        // Dimension requirements
        Set<String> dimensionKeys = Set.of(
                "automotive",
                "furniture",
                "home-garden",
                "travel-luggage",
                "sports-recreation"
        );

        boolean requiresDimensions = categories.stream()
                .anyMatch(dimensionKeys::contains);

        boolean missingDimensions =
                !StringUtils.hasText(productData.getHeight())
                        || !StringUtils.hasText(productData.getWidth())
                        || !StringUtils.hasText(productData.getDepth());

        if (requiresDimensions && missingDimensions) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    messageSource.getMessage(
                            "api.add_listing.product.error",
                            null,
                            LocaleContextHolder.getLocale()
                    )
            );
        }

        // Automotive item number requirement
        Set<String> itemNumberKeys = Set.of(
                "automotive-parts",
                "electronics",
                "home-appliances",
                "tools"
        );

        boolean requiresItemNumber = categories.stream()
                .anyMatch(itemNumberKeys::contains);

        boolean missingItemNumber =
                !StringUtils.hasText(productData.getItemNumber());


        if (requiresItemNumber && missingItemNumber) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    messageSource.getMessage(
                            "api.add_listing.product.item_number.error",
                            null,
                            LocaleContextHolder.getLocale()
                    )
            );
        }

        return listingTransactionService.editSellerProduct(jwt, productData);
    }

    @DeleteMapping("/listing/delete")
    public void deleteListing(@AuthenticationPrincipal Jwt jwt,Long listingId){
        listingTransactionService.deleteListing(jwt,listingId);
    }


}

