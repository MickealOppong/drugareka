package com.pages.service;

import com.pages.dto.*;
import com.pages.enums.*;
import com.pages.exception.InvalidOperationException;
import com.pages.model.*;
import com.pages.repository.ListingTransactionRepo;
import com.pages.specs.ListingSpecs;
import com.pages.util.UtilService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ListingTransactionService {

    private final ListingTransactionRepo listingTransactionRepo;
    private final AppUserDetailsService appUserDetailsService;
    private final MediaService mediaService;
    private final InventoryItemPriceService inventoryItemPriceService;
    private final SellerProfileService sellerProfileService;
    private final ProductCatalogService productCatalogService;
    private final PricingService pricingService;
    private final InventoryItemService inventoryItemService;
    private final CategoryService categoryService;
    private final BrandService brandService;
    private final ProductConditionService productConditionService;
    private final EmailNotificationService emailNotificationService;

    public ListingTransactionService(ListingTransactionRepo listingTransactionRepo, AppUserDetailsService appUserDetailsService, MediaService mediaService, InventoryItemPriceService inventoryItemPriceService, SellerProfileService sellerProfileService, ProductCatalogService productCatalogService, PricingService pricingService, InventoryItemService inventoryItemService, CategoryService categoryService, BrandService brandService, ProductConditionService productConditionService, EmailNotificationService emailNotificationService) {
        this.listingTransactionRepo = listingTransactionRepo;
        this.appUserDetailsService = appUserDetailsService;
        this.mediaService = mediaService;
        this.inventoryItemPriceService = inventoryItemPriceService;
        this.sellerProfileService = sellerProfileService;
        this.productCatalogService = productCatalogService;
        this.pricingService = pricingService;
        this.inventoryItemService = inventoryItemService;
        this.categoryService = categoryService;
        this.brandService = brandService;
        this.productConditionService = productConditionService;
        this.emailNotificationService = emailNotificationService;
    }

    public void CreateListing(ListTransRequest dto) {
        ListingTransaction newTransaction = new ListingTransaction(dto);
        listingTransactionRepo.save(newTransaction);
    }

    private ListingStatus getListingStatus(String status) {
        String cleanStatus = status.trim().toUpperCase();
        return switch (cleanStatus) {
            case "PUBLISH" -> ListingStatus.PUBLISHED;
            case "REMOVED" -> ListingStatus.REMOVED;
            case "PAUSED" -> ListingStatus.PAUSED;
            case "EXPIRED" -> ListingStatus.EXPIRED;
            default -> ListingStatus.DRAFT;
        };

    }


    @Transactional
    public ResponseDto<Object> editSellerProduct(Jwt jwt, ProductData productData) {
        try {

            // =====================================================
            // 1. AUTHENTICATION
            // =====================================================

            if (jwt == null) {
                return ResponseDto.builder()
                        .data(false)
                        .message("Not authorised")
                        .httpStatus(HttpStatus.UNAUTHORIZED.value())
                        .build();
            }

            if (productData == null || productData.getId() == null) {
                return ResponseDto.builder()
                        .data(false)
                        .message("Listing ID is required")
                        .httpStatus(HttpStatus.BAD_REQUEST.value())
                        .build();
            }


            // =====================================================
            // 2. GET CURRENT USER
            // =====================================================

            String username = jwt.getSubject();

            AppUser appUser =
                    appUserDetailsService.getAppUserByUsername(username);

            if (appUser == null) {
                return ResponseDto.builder()
                        .data(false)
                        .message("User not found")
                        .httpStatus(HttpStatus.NOT_FOUND.value())
                        .build();
            }


            // =====================================================
            // 3. GET SELLER PROFILE
            // =====================================================

            SellerProfile sellerProfile =
                    sellerProfileService.getSellerProfile(appUser.getId());


            // =====================================================
            // 4. FIND EXISTING LISTING
            // =====================================================

            ListingTransaction listing =
                    listingTransactionRepo
                            .findById(productData.getId())
                            .orElseThrow(() ->
                                    new IllegalArgumentException(
                                            "Listing not found"
                                    )
                            );


            // =====================================================
            // 5. GET INVENTORY
            // =====================================================

            InventoryItem inventoryItem = listing.getInventory();

            if (inventoryItem == null) {
                throw new IllegalStateException(
                        "Inventory item not found for listing"
                );
            }


            // =====================================================
            // 6. VERIFY SELLER OWNS THIS LISTING
            // =====================================================

            if (!inventoryItem.getSeller()
                    .getId()
                    .equals(sellerProfile.getId())) {

                return ResponseDto.builder()
                        .data(false)
                        .message("You are not authorised to edit this listing")
                        .httpStatus(HttpStatus.FORBIDDEN.value())
                        .build();
            }


            // =====================================================
            // 7. DON'T EDIT RESERVED OR SOLD ITEMS
            // =====================================================

            if (inventoryItem.getStatus() == InventoryStatus.RESERVED) {

                return ResponseDto.builder()
                        .data(false)
                        .message("This listing is currently reserved and cannot be edited.")
                        .httpStatus(HttpStatus.CONFLICT.value())
                        .build();
            }

            if (inventoryItem.getStatus() == InventoryStatus.SOLD) {

                return ResponseDto.builder()
                        .data(false)
                        .message("A sold listing cannot be edited.")
                        .httpStatus(HttpStatus.CONFLICT.value())
                        .build();
            }


            // =====================================================
            // 8. UPDATE PRODUCT CATALOG
            // =====================================================

            ProductCatalog productCatalog =
                    inventoryItem.getProductCatalog();

            if (productCatalog == null) {
                throw new IllegalStateException("Product catalog not found");
            }

            Brand retreivedBrand = brandService.findByNameOrCreate(productData.getBrand());

            if (!productCatalog.getBrand().getName().equalsIgnoreCase(retreivedBrand.getName())) {
                productCatalog.setBrand(retreivedBrand);
            }
            Category newCategory = categoryService.getCategoryBySlug(UtilService.formatNameToSlug(productData.getCategory()));

            if (!productCatalog.getCategory().getName().equalsIgnoreCase(newCategory.getName())) {
                productCatalog.setCategory(newCategory);
            }


            productCatalog.setName(productData.getName());
            productCatalog.setDescription(productData.getDescription());

            productCatalog.setSlug(UtilService.formatNameToSlug(productData.getName()));

            productCatalogService.save(productCatalog);


            // =====================================================
            // 9. UPDATE INVENTORY
            // =====================================================


            inventoryItem.setShippingMethod(inventoryItemService.getShippingMethod(productData.getShippingMethod()));


            inventoryItem.setDeliveryInfo(productData.getShippingInfo());

            ProductCondition newCondition = productConditionService.getConditionByName(productData.getCondition());

            if (!inventoryItem.getProductCondition().getName().equalsIgnoreCase(newCondition.getName())) {
                inventoryItem.setProductCondition(newCondition);
            }


            inventoryItem.setSku(productData.getSku());

            inventoryItemService.save(inventoryItem);


            // =====================================================
            // 10. UPDATE PRICE
            // =====================================================

            InventoryItemPrice currentPrice = inventoryItemPriceService.getPrices(inventoryItem.getId());

            BigDecimal oldPrice = currentPrice.getSellerNewPrice();

            BigDecimal newPrice = productData.getPrice();


            if (newPrice == null) {
                throw new IllegalArgumentException(
                        "Price is required"
                );
            }


            // Only create a new price history record when
            // the price actually changes.

            if (oldPrice == null ||
                    oldPrice.compareTo(newPrice) != 0) {

                BigDecimal newStorePrice =
                        pricingService.calculateStorePrice(newPrice);

                currentPrice.setSellerOldPrice(oldPrice);
                currentPrice.setSellerNewPrice(newPrice);
                currentPrice.setStoreOldPrice(currentPrice.getStoreNewPrice());
                currentPrice.setStoreNewPrice(newStorePrice);
                currentPrice.setInventoryItem(inventoryItem);
                currentPrice.setReason("Listing price updated");


                inventoryItemPriceService.savePrice(currentPrice);
            }


            // =====================================================
            // 11. UPDATE MEDIA
            // =====================================================


            mediaService.updateMedia(productData.getImages(),
                    productData.getImageSortOrder(),
                    inventoryItem);


            // =====================================================
            // 12. UPDATE LISTING STATUS
            // =====================================================

            listing.setListingStatus(
                    getListingStatus(productData.getStatus())
            );

            listingTransactionRepo.save(listing);


            // =====================================================
            // 13. RESPONSE
            // =====================================================

            return ResponseDto.builder()
                    .data(true)
                    .message("Listing updated successfully")
                    .httpStatus(HttpStatus.OK.value())
                    .build();

        } catch (Exception e) {

            log.error(
                    "Error updating seller listing {}",
                    productData != null
                            ? productData.getId()
                            : null,
                    e
            );

            return ResponseDto.builder()
                    .data(false)
                    .message(e.getMessage())
                    .httpStatus(HttpStatus.BAD_REQUEST.value())
                    .build();
        }
    }


    @jakarta.transaction.Transactional
    public ResponseDto<Object> addSellerProduct(Jwt jwt, ProductData productData) {
        try {

            if (jwt == null) {
                return ResponseDto.builder()
                        .httpStatus(HttpStatus.BAD_REQUEST.value())
                        .message("Not Authorised")
                        .build();
            }
                /*
                SELLER PROFILE
                 */

            String username = jwt.getSubject();
            AppUser appUser = appUserDetailsService.getAppUserByUsername(username);

            SellerProfileDto sellerProfileDto = SellerProfileDto.builder()
                    .user(appUser)
                    .status(appUser.isEnabled() ? SellerStatus.ACTIVE : SellerStatus.PENDING)
                    .build();

            SellerProfile sellerProfile = sellerProfileService.findOrCreateSellerProfile(sellerProfileDto);
                /*
                        PRODUCT
                 */
            ProductRequest productRequest = ProductRequest.builder()
                    .brand(productData.getBrand())
                    .category(productData.getCategory())
                    .description(productData.getDescription())
                    .name(productData.getName())
                    .slug(productData.getName())
                    .build();
            ProductCatalog savedProductCatalog = productCatalogService.findOrCreateProduct(productRequest);

                    /*
                        INVENTORY ITEM
                     */

            InventoryItemRequest inventoryItemRequest = InventoryItemRequest.builder()
                    .seller(sellerProfile)
                    .productCatalog(savedProductCatalog)
                    .shippingMethod(productData.getShippingMethod())
                    .condition(productData.getCondition())
                    .sku(productData.getSku())
                    .status(InventoryStatus.AVAILABLE)
                    .build();

            InventoryItem savedInventoryItem = inventoryItemService.addNewInventory(inventoryItemRequest);

            log.info("Saved inventory {}", savedInventoryItem);

                 /*
                    ITEM PRICE
                  */
            InventoryItemPriceDto inventoryItemPriceDto = InventoryItemPriceDto.builder()
                    .sellerOldPrice(productData.getPrice())
                    .sellerNewPrice(productData.getPrice())
                    .storeOldPrice(pricingService.calculateStorePrice(productData.getPrice()))
                    .storeNewPrice(pricingService.calculateStorePrice(productData.getPrice()))
                    .inventoryItem(savedInventoryItem)
                    .reason("Initial listing price")
                    .build();

            inventoryItemPriceService.addNewPrice(inventoryItemPriceDto);


              /*
                        PRODUCT IMAGES
               */
            mediaService.saveMedia(productData.getImages(), productData.getImageSortOrder(), savedInventoryItem);


            //LISTING TRANSACTION UPDATE
            ListTransRequest listTrans = ListTransRequest.builder()
                    .inventory(savedInventoryItem)
                    .listingStatus(getListingStatus(productData.getStatus()))
                    .build();
            CreateListing(listTrans);
            emailNotificationService.sendProductCreatedNotification(appUser.getUsername(),
                  appUser.getFirstName()+" "+appUser.getLastName(),  productData.getName(),BigDecimal.valueOf(Long.parseLong(String.valueOf(productData.getPrice()))));

            //return
            return ResponseDto.builder()
                    .data(true)
                    .message("Listing created")
                    .httpStatus(HttpStatus.OK.value())
                    .build();
        } catch (Exception e) {
            return ResponseDto.builder()
                    .data(false)
                    .message(e.getMessage())
                    .httpStatus(HttpStatus.BAD_REQUEST.value())
                    .build();
        }
    }

    @Transactional(readOnly = true)
    public List<ListTransResponse> getShopLandingListing(){
       return listingTransactionRepo
                .findTop8ByInventoryStatusAndListingStatusOrderByCreatedAtDesc(InventoryStatus.AVAILABLE,ListingStatus.PUBLISHED).stream()
                .map(listing -> {

                    Long inventoryId =
                            listing.getInventory().getId();


                    /*
                     * PRODUCT IMAGE
                     */
                    List<MediaResponse> media =
                            mediaService.getImageList(inventoryId);


                    /*
                     * PRICE
                     */
                    InventoryItemPriceResponse price =
                            inventoryItemPriceService
                                    .getPrices(inventoryId)
                                    .toPriceDto();


                    /*
                     * RESPONSE DTO
                     */
                    return ListingTransaction.toListTransResponse(
                            listing,
                            media,
                            price
                    );
                }).toList();

    }

    @Transactional(readOnly = true)
    public ListPageDto getMarketplaceCatalogFeed( String categoryQuery, int page, int size) {

        /*
         * =====================================================
         * CATEGORY FILTER
         * =====================================================
         *
         * "all" or null means no category filtering.
         */
        String targetCategory =
                categoryQuery == null || categoryQuery.isBlank()
                        || "all".equalsIgnoreCase(categoryQuery.trim())
                        ? null
                        : categoryQuery.trim();


        /*
         * =====================================================
         * PAGINATION
         * =====================================================
         */
        Pageable pageable = PageRequest.of(
                page == 0 ? page : page - 1,
                size,
                Sort.by(Sort.Direction.DESC, "createdAt")
        );


        /*
         * =====================================================
         * SPECIFICATION
         * =====================================================
         */
        Specification<ListingTransaction> specification =
                Specification
                        .where(ListingSpecs.hasListingStatus(ListingStatus.PUBLISHED))
                        .and(ListingSpecs.hasCategory(targetCategory))
                        .and(ListingSpecs.isNotSold());


        /*
         * =====================================================
         * FETCH + MAP PAGE
         * =====================================================
         */
        Page<ListTransResponse> listings =
                listingTransactionRepo
                        .findAll(specification, pageable)
                        .map(listing -> {

                            Long inventoryId =
                                    listing.getInventory().getId();


                            /*
                             * PRODUCT IMAGE
                             */
                            List<MediaResponse> media =
                                    mediaService.getImageList(inventoryId);


                            /*
                             * PRICE
                             */
                            InventoryItemPriceResponse price =
                                    inventoryItemPriceService
                                            .getPrices(inventoryId)
                                            .toPriceDto();


                            /*
                             * RESPONSE DTO
                             */
                            return ListingTransaction.toListTransResponse(
                                    listing,
                                    media,
                                    price
                            );
                        });


        /*
         * =====================================================
         * PAGE RESPONSE
         * =====================================================
         */


        return ListPageDto.builder()
                .listings(listings.getContent())
                .page(listings.getNumber())
                .pageSize(listings.getSize())
                .totalPages(listings.getTotalPages())
                .totalElements(listings.getTotalElements())
                .build();


    }


    @Transactional(readOnly = true)
    public ListPageDto storeCatalogFeed(Jwt jwt, int page, int size) {

        if (jwt == null) {
            return ListPageDto.builder()
                    .build();
        }

        /*
         * =====================================================
         * PAGINATION
         * =====================================================
         */
        Pageable pageable = PageRequest.of(
                page == 0 ? page : page - 1,
                size,
                Sort.by(Sort.Direction.DESC, "createdAt")
        );



        /*
         * =====================================================
         * FETCH + MAP PAGE
         * =====================================================
         */


        Page<ListTransResponse> listings =
                listingTransactionRepo
                        .findAll(pageable)
                        .map(listing -> {
                            Long inventoryId =
                                    listing.getInventory().getId();

                            /*
                             * PRODUCT IMAGE
                             */
                            List<MediaResponse> media =
                                    mediaService.getImageList(inventoryId);

                            /*
                             * PRICE
                             */
                            InventoryItemPriceResponse price =
                                    inventoryItemPriceService
                                            .getPrices(inventoryId)
                                            .toPriceDto();


                            /*
                             * RESPONSE DTO
                             */
                            return ListingTransaction.toListTransResponse(
                                    listing,
                                    media,
                                    price
                            );
                        });


        /*
         * =====================================================
         * PAGE RESPONSE
         * =====================================================
         */


        return ListPageDto.builder()
                .listings(listings.getContent())
                .page(listings.getNumber())
                .pageSize(listings.getSize())
                .totalPages(listings.getTotalPages())
                .totalElements(listings.getTotalElements())
                .build();


    }

    @Transactional(readOnly = true)
    public ListPageDto myCatalogFeed(Jwt jwt, int page, int size) {

        if (jwt == null) {
            return ListPageDto.builder()
                    .build();
        }

        AppUser currentUser = appUserDetailsService.getAppUserByUsername(jwt.getSubject());
        Long currentUserId = currentUser.getId();
        /*
         * =====================================================
         * PAGINATION
         * =====================================================
         */
        Pageable pageable = PageRequest.of(
                page - 1,
                size,
                Sort.by(Sort.Direction.DESC, "createdAt")
        );



        /*
         * =====================================================
         * FETCH + MAP PAGE
         * =====================================================
         */
        Page<ListTransResponse> listings =
                listingTransactionRepo
                        .findByInventorySellerUserId(currentUserId, pageable)
                        .map(listing -> {
                            Long inventoryId =
                                    listing.getInventory().getId();

                            /*
                             * PRODUCT IMAGE
                             */
                            List<MediaResponse> media =
                                    mediaService.getImageList(inventoryId);


                            /*
                             * PRICE
                             */
                            InventoryItemPriceResponse price =
                                    inventoryItemPriceService
                                            .getPrices(inventoryId)
                                            .toPriceDto();


                            /*
                             * RESPONSE DTO
                             */
                            return ListingTransaction.toListTransResponse(
                                    listing,
                                    media,
                                    price
                            );
                        });

        /*
         * =====================================================
         * PAGE RESPONSE
         * =====================================================
         */

        return ListPageDto.builder()
                .listings(listings.getContent())
                .page(listings.getNumber())
                .pageSize(listings.getSize())
                .totalPages(listings.getTotalPages())
                .totalElements(listings.getTotalElements())
                .build();


    }

    @Transactional(readOnly = true)
    public ListingTransaction getListingTransaction(Long listingId, ListingStatus listingStatus) {
        return listingTransactionRepo.findByIdAndListingStatus(listingId, listingStatus).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<ListTransResponse> getProductListings() {

        return listingTransactionRepo
                .findByListingStatus(ListingStatus.PUBLISHED).stream().map(listing -> {
                          /*
                    PRODUCT IMAGES
                     */
                    Long inventoryId = listing.getInventory() != null ? listing.getInventory().getId() : null;
                    List<MediaResponse> media = mediaService.getImageList(inventoryId);
                    /*
                    PRICE DATA
                     */
                    InventoryItemPriceResponse price = inventoryItemPriceService.getPrices(inventoryId).toPriceDto();

                    return ListingTransaction.toListTransResponse(listing, media, price);
                }).toList();


    }

    @Transactional(readOnly = true)
    public ListTransResponse getProductByListingId(Long listingId) {
        ListingTransaction listingTransaction = listingTransactionRepo.findById(listingId).orElse(null);
        if (listingTransaction != null) {

              /*
                    MEDIA DATA
                     */
            List<MediaResponse> mediaList = mediaService.getImageList(listingTransaction.getInventory().getId());

              /*
                    PRICE DATA
                     */
            InventoryItemPriceResponse price = inventoryItemPriceService.getPrices(listingTransaction.getInventory().getId()).toPriceDto();
            return ListingTransaction.toListTransResponse(listingTransaction, mediaList, price);
        }
        return ListTransResponse.builder().build();
    }

    @Transactional(readOnly = true)
    public Long getSellerPublishedListingCount(Long sellerId) {
        return listingTransactionRepo.countByInventorySellerIdAndInventoryStatusAndListingStatus(sellerId, InventoryStatus.AVAILABLE, ListingStatus.PUBLISHED);
    }

    @Transactional(readOnly = true)
    public List<ListTransResponse> getRecentViews(List<Long> ids, Jwt jwt) {
        if (jwt != null) {
            AppUser currentUser = appUserDetailsService.getAppUserByUsername(jwt.getSubject());

            return listingTransactionRepo.findAllById(ids).stream()
                    .filter(item -> !item.getInventory().getSeller().getUser().getId().equals(currentUser.getId()))
                    .map(listing -> {

                     /*
                    PRICE DATA
                     */
                        InventoryItemPriceResponse price = inventoryItemPriceService.getPrices(listing.getInventory().getId()).toPriceDto();

                        List<MediaResponse> mediaList = mediaService.getImageList(listing.getInventory().getId());
                        return ListingTransaction.toListTransResponse(listing, mediaList, price);
                    }).toList();
        }
        return null;
    }

    @Transactional
    public void deleteListing(Jwt jwt, Long listing) {
        if (jwt != null) {

            listingTransactionRepo.findById(listing)
                    .ifPresent(item->{

                        InventoryItem inventoryItem = item.getInventory();
                        if(inventoryItem.getStatus().equals(InventoryStatus.RESERVED)){
                            throw new InvalidOperationException("Product has been reserved");
                        }
                        if(inventoryItem.getStatus().equals(InventoryStatus.SOLD)){
                            throw new InvalidOperationException("Product already sold");
                        }
                        listingTransactionRepo.delete(item);

                        inventoryItemPriceService.deleteInventoryPrice(inventoryItem);
                        try {
                            mediaService.deleteAllByInventoryItem(inventoryItem);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                        //productCatalogService.deleteCatalog(inventoryItem.getProductCatalog());
                        inventoryItem.setProductCatalog(null);

                        inventoryItemService.deleteInventory(inventoryItem);

                    });
        }
    }



    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ListingTransaction recentListing(Jwt jwt) {

        if (jwt == null) {
            return null;

        }

        AppUser currentUser = appUserDetailsService.getAppUserByUsername(jwt.getSubject());
        SellerProfile seller = sellerProfileService.getSellerProfile(currentUser.getId());
        return listingTransactionRepo.findFirstByInventorySellerIdAndListingStatusOrderByCreatedAtDesc(seller.getId(),ListingStatus.PUBLISHED)
                .orElse(null);
    }
}
