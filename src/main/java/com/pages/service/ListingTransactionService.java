package com.pages.service;

import com.pages.dto.*;
import com.pages.enums.*;
import com.pages.exception.InvalidOperationException;
import com.pages.exception.PhotoNotFoundException;
import com.pages.model.*;
import com.pages.repository.GlobalAddressRepo;
import com.pages.repository.ListingOfferRepo;
import com.pages.repository.ListingTransactionRepo;
import com.pages.specs.ListingSpecs;
import com.pages.util.GlobalAddress;
import com.pages.util.UtilService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static com.pages.specs.ListingSpecs.hasBrand;


@Slf4j
@Service
@RequiredArgsConstructor
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
    private final ListingOfferRepo listingOfferRepo;
    private final MessageSource messageSource;
    private final ShippingOptionService shippingOptionService;


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
    public boolean editSellerProduct(Jwt jwt, ProductData productData) {
        Locale locale = LocaleContextHolder.getLocale();
        try {
            // =====================================================
            // 1. AUTHENTICATION
            // =====================================================
            if (jwt == null) {
                String msg = messageSource.getMessage("api.listing.auth.required", null, "Authentication required.", locale);
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
            }

            if (productData == null || productData.getId() == null) {
                String msg = messageSource.getMessage("api.listing.id.required", null, "Listing ID is required.", locale);
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
            }

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

            // =====================================================
            // 2. GET CURRENT USER
            // =====================================================
            String username = jwt.getSubject();
            AppUser appUser = appUserDetailsService.getAppUserByUsername(username);
            if (appUser == null) {
                String msg = messageSource.getMessage("api.listing.user.not_found", null, "User profile not found.", locale);
                 throw new ResponseStatusException(HttpStatus.BAD_REQUEST,msg);
            }

            // =====================================================
            // 3. GET SELLER PROFILE
            // =====================================================
            SellerProfile sellerProfile = sellerProfileService.getSellerProfile(appUser.getId());

            //prevent listing for problematic sellers
            boolean isNotEligible =sellerProfile.getStatus()==SellerStatus.BLOCKED
                    ||sellerProfile.getStatus()==SellerStatus.SUSPENDED
                    ||sellerProfile.getStatus()==SellerStatus.PENDING;

            if(isNotEligible){
                throw new DuplicateKeyException(
                        messageSource.getMessage("api.add_listing.seller.forbidden",null,LocaleContextHolder.getLocale())
                );
            }


            // =====================================================
            // 4. FIND EXISTING LISTING
            // =====================================================
            ListingTransaction listing = listingTransactionRepo.findById(productData.getId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            messageSource.getMessage("api.listing.not_found", null, "Listing not found.", locale)
                    ));

            // =====================================================
            // 5. GET INVENTORY
            // =====================================================
            InventoryItem inventoryItem = listing.getInventory();
            if (inventoryItem == null) {
                throw new IllegalStateException(
                        messageSource.getMessage("api.listing.inventory.missing", null, "Inventory records missing for listing.", locale)
                );
            }

            // =====================================================
            // 6. VERIFY SELLER OWNS THIS LISTING
            // =====================================================
            boolean isAdmin = appUser.getUserRoles().stream()
                    .map(AppUserRole::getRole)
                    .anyMatch(role -> role.equalsIgnoreCase("ROLE_ADMIN"));

            if (isAdmin) {
                log.info("Security Gate: System Administrator [{}] granted moderation access to listing ID: {}",
                        appUser.getUsername(), productData.getId());
            } else {

                if (!inventoryItem.getSeller().getId().equals(sellerProfile.getId())) {
                    log.warn("Security Shield: Unauthorized modification attempt on Listing [{}] by Vendor [{}]",
                            inventoryItem.getId(), sellerProfile.getId());
                    String msg = messageSource.getMessage("api.listing.edit.unauthorized", null, "You are not authorized to edit this listing.", locale);
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,msg);
                }
            }

            // =====================================================
            // 7. DON'T EDIT RESERVED OR SOLD ITEMS
            // =====================================================
            if (inventoryItem.getStatus() == InventoryStatus.RESERVED) {
                String msg = messageSource.getMessage("api.listing.edit.reserved", null, "This listing is currently reserved and cannot be modified.", locale);
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,msg);
            }

            if (inventoryItem.getStatus() == InventoryStatus.SOLD) {

                String msg = messageSource.getMessage("api.listing.edit.sold", null, "A sold listing cannot be edited.", locale);
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,msg);
            }
// =====================================================
// 8. UPDATE PRODUCT CATALOG
// =====================================================
            ProductCatalog productCatalog = inventoryItem.getProductCatalog();
            if (productCatalog == null) {
                throw new IllegalStateException(
                        messageSource.getMessage("api.listing.catalog.missing", null, "Product catalog entry not found.", locale)
                );
            }
            Brand retreivedBrand = brandService.findByNameOrCreate(productData.getBrand());
            if (!productCatalog.getBrand().getName().equalsIgnoreCase(retreivedBrand.getName())) {
                productCatalog.setBrand(retreivedBrand);
            }
            Category newCategory = categoryService.getCategoryByPath(productData.getCategoryPath());
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
            inventoryItem.setDeliveryInfo(productData.getShippingInfo());
            inventoryItem.setItemSize(inventoryItemService.getItemDimension(productData.getItemSize()));
            inventoryItem.setPersonalCollectionAvailable(productData.isPersonalCollectionAvailable());
            inventoryItem.setCollectionCity(productData.getCollectionCity());
            inventoryItem.setQuantity(Long.valueOf(productData.getQuantity()));

            if (StringUtils.hasText(productData.getProductionYear())) {
                inventoryItem.setProductionYear(productData.getProductionYear());
            }
            if (StringUtils.hasText(productData.getWidth())) {
                inventoryItem.setWidth(productData.getWidth());
            }
            if (StringUtils.hasText(productData.getHeight())) {
                inventoryItem.setHeight(productData.getHeight());
            }
            if (StringUtils.hasText(productData.getDepth())) {
                inventoryItem.setDepth(productData.getDepth());
            }
            if (StringUtils.hasText(productData.getModel())) {
                inventoryItem.setModel(productData.getModel());
            }
            if (StringUtils.hasText(productData.getItemNumber())) {
                inventoryItem.setItemNumber(productData.getItemNumber());
            }
            ProductCondition newCondition = productConditionService.getConditionByName(productData.getCondition());
            if (!inventoryItem.getProductCondition().getName().equalsIgnoreCase(newCondition.getName())) {
                inventoryItem.setProductCondition(newCondition);
            }
            inventoryItem.setQuantity(productData.getQuantity());
            inventoryItem.setSku(productData.getSku());
            inventoryItemService.save(inventoryItem);
// =====================================================
// 10. UPDATE PRICE
// =====================================================
            InventoryItemPrice currentPrice = inventoryItemPriceService.getPrices(inventoryItem.getId());
            BigDecimal oldPrice = currentPrice.getSellerNewPrice();
            BigDecimal newPrice = productData.getPrice();
            BigDecimal minPrice = productData.getMinPrice();
            if (newPrice == null) {
                throw new IllegalArgumentException(
                        messageSource.getMessage("api.listing.product.price.empty", null, "Price is required.", locale)
                );
            }
            currentPrice.setSellerOldPrice(oldPrice);
            currentPrice.setSellerNewPrice(newPrice);
            currentPrice.setServiceCharge(pricingService.getServiceCharge(newPrice));
            currentPrice.setInventoryItem(inventoryItem);
            currentPrice.setSellerMinPrice(minPrice);
            currentPrice.setReason("Listing price updated");
            inventoryItemPriceService.savePrice(currentPrice);
// =====================================================
// 11. UPDATE MEDIA
// =====================================================
            mediaService.updateMediaFile(productData.getImages(), productData.getImageSortOrder(), inventoryItem);
// =====================================================
// 12. UPDATE LISTING STATUS
// =====================================================
            ListingStatus status = getListingStatus(productData.getStatus());
            listing.setListingStatus(status);
            listing.setConfirmationStatus(status==ListingStatus.PUBLISHED?null:listing.getConfirmationStatus());
            listingTransactionRepo.save(listing);

// =====================================================
// 13. RESPONSE
// =====================================================
            String successMsg = messageSource.getMessage("api.listing.edit.success", null, "Listing updated successfully.", locale);
           return true;
        } catch (ResponseStatusException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,e.getReason());
        } catch (Exception e) {
            if(isDuplicateMediaItemFileHash(e)){
                throw new DuplicateKeyException(
                        messageSource.getMessage("api.add_listing.product.duplicate.error",null,LocaleContextHolder.getLocale())
                );
            }
            throw e;
        }
    }


    @Transactional
    public ListingTransaction editSellerProduct(
            SellerProfile seller,
            ProductData productData) {

        try {
            Locale locale = LocaleContextHolder.getLocale();

            // 1. Validate input
            if (seller == null) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
            }

            if (productData == null || productData.getId() == null) {
                String msg = messageSource.getMessage(
                        "api.listing.id.required",
                        null,
                        "Listing ID is required.",
                        locale
                );

                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
            }

            // 2. Validate product images
            if (productData.getImages() == null
                    || productData.getImages().length == 0) {

                throw new PhotoNotFoundException(
                        "api.add_listing.product.image.error"
                );
            }

            // 3. Safely extract category path
            List<String> categories = productData.getCategoryPath() == null
                    ? Collections.emptyList()
                    : Arrays.stream(productData.getCategoryPath().split("/"))
                    .map(String::trim)
                    .map(value -> value.toLowerCase(Locale.ROOT))
                    .filter(StringUtils::hasText)
                    .toList();

            // 4. Determine category requirements
            boolean requiresDimensions = categories.stream()
                    .anyMatch(DIMENSION_REQUIRED_CATEGORIES::contains);

            boolean requiresModel = categories.stream()
                    .anyMatch(MODEL_REQUIRED_CATEGORIES::contains);

            boolean requiresItemNumber = categories.stream()
                    .anyMatch(ITEM_NUMBER_REQUIRED_CATEGORIES::contains);

            // 5. Validate dimensions
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
                                locale
                        )
                );
            }

            // 6. Validate model
            if (requiresModel && !StringUtils.hasText(productData.getModel())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        messageSource.getMessage(
                                "api.add_listing.product.model.error",
                                null,
                                locale
                        )
                );
            }

            // 7. Validate OEM / part / product number
            if (requiresItemNumber
                    && !StringUtils.hasText(productData.getItemNumber())) {

                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        messageSource.getMessage(
                                "api.add_listing.automotive.item_number.error",
                                null,
                                locale
                        )
                );
            }

            // 8. Validate personal collection city
            if (productData.isPersonalCollectionAvailable()
                    && !StringUtils.hasText(productData.getCollectionCity())) {

                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        messageSource.getMessage(
                                "api.add_listing.city.error",
                                null,
                                locale
                        )
                );
            }

            // 9. Validate seller eligibility
            boolean isNotEligible =
                    seller.getStatus() == SellerStatus.BLOCKED
                            || seller.getStatus() == SellerStatus.SUSPENDED
                            || seller.getStatus() == SellerStatus.PENDING;

            if (isNotEligible) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        messageSource.getMessage(
                                "api.add_listing.seller.forbidden",
                                null,
                                locale
                        )
                );
            }

            // 10. Find existing listing
            ListingTransaction listing = listingTransactionRepo
                    .findById(productData.getId())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            messageSource.getMessage(
                                    "api.listing.not_found",
                                    null,
                                    "Listing not found.",
                                    locale
                            )
                    ));
            // 11. Get inventory
            InventoryItem inventoryItem = listing.getInventory();

            if (inventoryItem == null) {
                throw new IllegalStateException(
                        messageSource.getMessage(
                                "api.listing.inventory.missing",
                                null,
                                "Inventory records missing for listing.",
                                locale
                        )
                );
            }

            // 12. Verify seller ownership
            if (inventoryItem.getSeller() == null
                    || !inventoryItem.getSeller().getId().equals(seller.getId())) {

                log.warn(
                        "Unauthorized modification attempt on Listing [{}] by Seller [{}]",
                        listing.getId(),
                        seller.getId()
                );

                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        messageSource.getMessage(
                                "api.listing.edit.unauthorized",
                                null,
                                "You are not authorized to edit this listing.",
                                locale
                        )
                );
            }

            // 13. Prevent editing reserved or sold items
            if (inventoryItem.getStatus() == InventoryStatus.RESERVED) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        messageSource.getMessage(
                                "api.listing.edit.reserved",
                                null,
                                "This listing is currently reserved and cannot be modified.",
                                locale
                        )
                );
            }

            if (inventoryItem.getStatus() == InventoryStatus.SOLD) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        messageSource.getMessage(
                                "api.listing.edit.sold",
                                null,
                                "A sold listing cannot be edited.",
                                locale
                        )
                );
            }

            // 14. Update product catalog
            ProductCatalog productCatalog = inventoryItem.getProductCatalog();

            if (productCatalog == null) {
                throw new IllegalStateException(
                        messageSource.getMessage(
                                "api.listing.catalog.missing",
                                null,
                                "Product catalog entry not found.",
                                locale
                        )
                );
            }

            Brand retrievedBrand = brandService.findByNameOrCreate(productData.getBrand());

            if (productCatalog.getBrand() == null
                    || !productCatalog.getBrand().getName()
                    .equalsIgnoreCase(retrievedBrand.getName())) {

                productCatalog.setBrand(retrievedBrand);
            }

            Category newCategory = categoryService.getCategoryByPath(
                    productData.getCategoryPath()
            );

            if (productCatalog.getCategory() == null
                    || !productCatalog.getCategory().getName()
                    .equalsIgnoreCase(newCategory.getName())) {

                productCatalog.setCategory(newCategory);
            }

            productCatalog.setName(productData.getName());
            productCatalog.setDescription(productData.getDescription());
            productCatalog.setSlug(UtilService.formatNameToSlug(productData.getName()));

            productCatalogService.save(productCatalog);

            // 15. Update inventory
            inventoryItem.setDeliveryInfo(productData.getShippingInfo());
            inventoryItem.setItemSize(
                    inventoryItemService.getItemDimension(productData.getItemSize())
            );

            inventoryItem.setPersonalCollectionAvailable(
                    productData.isPersonalCollectionAvailable()
            );
            inventoryItem.setQuantity(Long.valueOf(productData.getQuantity()));

            inventoryItem.setCollectionCity(
                    productData.isPersonalCollectionAvailable()
                            ? productData.getCollectionCity()
                            : null
            );

            if (StringUtils.hasText(productData.getProductionYear())) {
                inventoryItem.setProductionYear(productData.getProductionYear());
            }

            if (StringUtils.hasText(productData.getWidth())) {
                inventoryItem.setWidth(productData.getWidth());
            }

            if (StringUtils.hasText(productData.getHeight())) {
                inventoryItem.setHeight(productData.getHeight());
            }

            if (StringUtils.hasText(productData.getDepth())) {
                inventoryItem.setDepth(productData.getDepth());
            }

            if (StringUtils.hasText(productData.getModel())) {
                inventoryItem.setModel(productData.getModel());
            }

            if (StringUtils.hasText(productData.getItemNumber())) {
                inventoryItem.setItemNumber(productData.getItemNumber());
            }

            ProductCondition newCondition =
                    productConditionService.getConditionByName(productData.getCondition());

            if (inventoryItem.getProductCondition() == null
                    || !inventoryItem.getProductCondition().getName()
                    .equalsIgnoreCase(newCondition.getName())) {

                inventoryItem.setProductCondition(newCondition);
            }

            inventoryItem.setQuantity(productData.getQuantity());
            inventoryItem.setSku(productData.getSku());

            inventoryItemService.save(inventoryItem);

            // 16. Update price
            InventoryItemPrice currentPrice =
                    inventoryItemPriceService.getPrices(inventoryItem.getId());

            BigDecimal oldPrice = currentPrice.getSellerNewPrice();
            BigDecimal newPrice = productData.getPrice();
            BigDecimal minPrice = productData.getMinPrice();

            if (newPrice == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        messageSource.getMessage(
                                "api.listing.product.price.empty",
                                null,
                                "Price is required.",
                                locale
                        )
                );
            }

            currentPrice.setSellerOldPrice(oldPrice);
            currentPrice.setSellerNewPrice(newPrice);
            currentPrice.setServiceCharge(pricingService.getServiceCharge(newPrice));
            currentPrice.setInventoryItem(inventoryItem);
            currentPrice.setSellerMinPrice(minPrice);
            currentPrice.setReason("Listing price updated");

            inventoryItemPriceService.savePrice(currentPrice);

            // 17. Update media
            mediaService.updateMediaFile(
                    productData.getImages(),
                    productData.getImageSortOrder(),
                    inventoryItem
            );

            // 18. Update listing status
            listing.setListingStatus(getListingStatus(productData.getStatus()));

            return listingTransactionRepo.save(listing);
        }catch (Exception e){
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,e.getMessage());
        }
    }



    @Transactional
    public ResponseDto<Object> addSellerProduct(Jwt jwt, ProductData productData) {
        Locale locale = LocaleContextHolder.getLocale();
        try {
            if (jwt == null) {
                String msg = messageSource.getMessage("api.listing.auth.required", null, "Authentication required.", locale);
                return ResponseDto.builder()
                        .httpStatus(HttpStatus.UNAUTHORIZED.value())
                        .message(msg)
                        .build();
            }

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

            String username = jwt.getSubject();
            AppUser appUser = appUserDetailsService.getAppUserByUsername(username);

            SellerProfileDto sellerProfileDto = SellerProfileDto.builder()
                    .user(appUser)
                    .totalSales(BigDecimal.ZERO)
                    .dateOfBirth(appUser.getDateOfBirth())
                    .fullLegalName(appUser.getFirstName() + " " + appUser.getLastName())
                    .status(SellerStatus.ACTIVE )
                    .identityVerified(true)
                    .build();
            SellerProfile sellerProfile = sellerProfileService.findOrCreateSellerProfile(sellerProfileDto);

            //prevent listing for problematic sellers
            boolean isNotEligible =sellerProfile.getStatus()==SellerStatus.BLOCKED
                    ||sellerProfile.getStatus()==SellerStatus.SUSPENDED
                    ||sellerProfile.getStatus()==SellerStatus.PENDING;

            if(isNotEligible){
                throw new DuplicateKeyException(
                        messageSource.getMessage("api.add_listing.seller.forbidden",null,LocaleContextHolder.getLocale())
                );
            }

            ProductRequest productRequest = ProductRequest.builder()
                    .brand(productData.getBrand())
                    .categoryPath(productData.getCategoryPath())
                    .description(productData.getDescription())
                    .name(productData.getName())
                    .slug(productData.getName())
                    .build();
            ProductCatalog savedProductCatalog = productCatalogService.findOrCreateProduct(productRequest);
            InventoryItemRequest inventoryItemRequest = InventoryItemRequest.builder()
                    .seller(sellerProfile)
                    .productCatalog(savedProductCatalog)
                    .condition(productData.getCondition())
                    .sku(productData.getSku())
                    .shippingInfo(productData.getShippingInfo())
                    .itemSize(productData.getItemSize())
                    .depth(productData.getDepth())
                    .quantity(productData.getQuantity())
                    .width(productData.getWidth())
                    .height(productData.getHeight())
                    .quantity(productData.getQuantity())
                    .personalCollectionAvailable(productData.isPersonalCollectionAvailable())
                    .collectionCity(productData.getCollectionCity())
                    .productionYear(productData.getProductionYear())
                    .model(productData.getModel())
                    .itemNumber(productData.getItemNumber())
                    .status(InventoryStatus.AVAILABLE)
                    .build();
            InventoryItem savedInventoryItem = inventoryItemService.addNewInventory(inventoryItemRequest);


            //price
            InventoryItemPriceDto inventoryItemPriceDto = InventoryItemPriceDto.builder()
                    .sellerOldPrice(productData.getPrice())
                    .sellerNewPrice(productData.getPrice())
                    .inventoryItem(savedInventoryItem)
                    .serviceCharge(pricingService.getServiceCharge(productData.getPrice()))
                    .sellerMinPrice(productData.getMinPrice())
                    .reason("Initial listing price")
                    .build();
            inventoryItemPriceService.addNewPrice(inventoryItemPriceDto);
            //product images
            mediaService.uploadProductImage(productData.getImages(), productData.getImageSortOrder(), savedInventoryItem);
            ListTransRequest listTrans = ListTransRequest.builder()
                    .inventory(savedInventoryItem)
                    .listingStatus(getListingStatus(productData.getStatus()))
                    .build();
            CreateListing(listTrans);
            //notifications
            emailNotificationService.sendProductCreatedNotification(appUser.getUsername(),
                    appUser.getFirstName() + " " + appUser.getLastName(), productData.getName(), BigDecimal.valueOf(Long.parseLong(String.valueOf(productData.getPrice()))));
            String successMsg = messageSource.getMessage("api.listing.create.success", null, "Listing created successfully.", locale);
            return ResponseDto.builder()
                    .data(true)
                    .message(successMsg)
                    .httpStatus(HttpStatus.OK.value())
                    .build();
        } catch (Exception e) {
            if(isDuplicateMediaItemFileHash(e)){
                throw new DuplicateKeyException(
                      messageSource.getMessage("api.add_listing.product.duplicate.error",null,LocaleContextHolder.getLocale())
                );
            }
            throw e;
        }
    }

    private boolean isDuplicateMediaItemFileHash(Throwable ex) {
        Throwable cause = ex;

        while (cause != null) {
            String message = cause.getMessage();

            if (message != null
                    && message.contains("uk_media_item_file_hash")) {
                return true;
            }

            cause = cause.getCause();
        }

        return false;
    }
    @Transactional(readOnly = true)
    public List<ListTransResponse> getShopLandingListing() {
        return listingTransactionRepo
                .findTop8ByInventoryStatusAndListingStatusOrderByCreatedAtDesc(InventoryStatus.AVAILABLE, ListingStatus.PUBLISHED).stream()
                .map(listing -> {
                    Long inventoryId = listing.getInventory().getId();
                    List<MediaResponse> media = mediaService.getImageList(inventoryId);
                    InventoryItemPrice price = inventoryItemPriceService.getPrices(inventoryId);
                    return ListingTransaction.toListTransResponse(listing, media, price);
                }).toList();
    }
    @Transactional(readOnly = true)
    public ListPageDto getMarketplaceCatalogFeed(QueryDto dto) {
        String targetCategory = dto.category() == null || dto.category().isBlank() || "all".equalsIgnoreCase(dto.category().trim()) ? null : dto.category().trim();
        Long categoryId = targetCategory != null ? Long.parseLong(targetCategory.substring(targetCategory.lastIndexOf("_") + 1)) : 0;
        Pageable pageable = PageRequest.of(
                dto.page() == 0 ? 0 : dto.page() - 1,
                dto.size(),
                Sort.by(Sort.Direction.DESC, "createdAt")
        );


        Specification<ListingTransaction> specification = Specification
                .where(ListingSpecs.hasListingStatus(ListingStatus.PUBLISHED))
                .and(ListingSpecs.hasCategoryId(categoryId))
                .and(ListingSpecs.matchesSearch(dto.search()))
                .and(ListingSpecs.hasCondition(dto.condition()))
                .and(ListingSpecs.isPersonalCollectionAvailable(dto.isCollectionAvailable()))
                .and(ListingSpecs.hasCollectionCity(dto.collectionCity()))
                .and(ListingSpecs.isNotSold())
                .and(hasBrand(dto.brand()));
        Page<ListTransResponse> listings = listingTransactionRepo.findAll(specification, pageable)
                .map(listing -> {
                    Long inventoryId = listing.getInventory().getId();
                    List<MediaResponse> media = mediaService.getImageList(inventoryId);
                    InventoryItemPrice price = inventoryItemPriceService.getPrices(inventoryId);
                    return ListingTransaction.toListTransResponse(listing, media, price);
                });
        return ListPageDto.builder()
                .listings(listings.getContent())
                .page(listings.getNumber())
                .pageSize(listings.getSize())
                .totalPages(listings.getTotalPages())
                .accumulatedItems(getAccumulatedItemsFromPage(listings.getNumber(),listings.getSize(),listings.getTotalElements()))
                .totalElements(listings.getTotalElements())
                .build();
    }
    @Transactional(readOnly = true)
    public ListPageDto storeCatalogFeed(Jwt jwt, int page, int size) {
        if (jwt == null) {
            return ListPageDto.builder().build();
        }
        Pageable pageable = PageRequest.of(
                page == 0 ? page : page - 1,
                size,
                Sort.by(Sort.Direction.DESC, "createdAt")
        );
        Page<ListTransResponse> listings = listingTransactionRepo.findAll(pageable)
                .map(listing -> {
                    Long inventoryId = listing.getInventory().getId();
                    List<MediaResponse> media = mediaService.getImageList(inventoryId);
                    InventoryItemPrice price = inventoryItemPriceService.getPrices(inventoryId);
                    return ListingTransaction.toListTransResponse(listing, media, price);
                });
        return ListPageDto.builder()
                .listings(listings.getContent())
                .page(listings.getNumber())
                .pageSize(listings.getSize())
                .accumulatedItems(getAccumulatedItemsFromPage(listings.getNumber(),listings.getSize(),listings.getTotalElements()))
                .totalPages(listings.getTotalPages())
                .totalElements(listings.getTotalElements())
                .build();
    }
    @Transactional
    public ResponseDto<Object> validateBuyerOffer(Jwt jwt, Long listingId, BigDecimal offer) {
        Locale locale = LocaleContextHolder.getLocale();
        if (jwt == null) {
            String msg = messageSource.getMessage("api.listing.offer.auth.required", null, "Invalid operation, user must log in to make an offer.", locale);
            return ResponseDto.builder()
                    .data(false)
                    .message(msg)
                    .httpStatus(HttpStatus.BAD_REQUEST.value())
                    .build();
        }
        AppUser appUser = appUserDetailsService.getAppUserByUsername(jwt.getSubject());
        Long userId = appUser.getId();
        if (offer == null || offer.signum() <= 0) {
            String msg = messageSource.getMessage("api.listing.offer.amount.invalid", null, "Invalid offer amount value provided.", locale);
            return ResponseDto.builder()
                    .data(false)
                    .message(msg)
                    .httpStatus(HttpStatus.BAD_REQUEST.value())
                    .build();
        }
        InventoryItem inventory = listingTransactionRepo.findById(listingId)
                .map(ListingTransaction::getInventory)
                .orElse(null);
        if (inventory != null) {
            var priceData = inventoryItemPriceService.getPrices(inventory.getId());
            BigDecimal minPrice = priceData.getSellerMinPrice() != null ? priceData.getSellerMinPrice() : priceData.getSellerNewPrice();
            BigDecimal currentPrice = priceData.getSellerNewPrice() != null ? priceData.getSellerNewPrice() : BigDecimal.ZERO;
            boolean withinFloorLimit = offer.compareTo(minPrice) >= 0;
            boolean belowAskingPrice = offer.compareTo(currentPrice) <= 0;
            Instant offerValidTo = Instant.now().plus(15, ChronoUnit.MINUTES);
            if (withinFloorLimit && belowAskingPrice) {
                ListingOffer listingOffer = ListingOffer.builder()
                        .status(OfferStatus.ACCEPTED)
                        .listingId(listingId)
                        .buyerId(userId)
                        .offeredPrice(offer)
                        .expiresAt(offerValidTo)
                        .build();
                listingOfferRepo.save(listingOffer);
                String msg = messageSource.getMessage("api.listing.offer.status.accepted", null,
                        "Offer accepted, you have 30 minutes to checkout.", locale);


                OfferResponse offerDto = OfferResponse.builder()
                        .price(offer)
                        .serviceCharge(pricingService.getServiceCharge(offer))
                        .shipping(shippingOptionService.calculateSingleShippingPrice(inventory,DeliveryOption.HOME_COURIER.name()))
                        .build();
                return ResponseDto.builder()
                        .data(offerDto)
                        .message(msg)
                        .httpStatus(HttpStatus.OK.value())
                        .build();
            }
            String msg = messageSource.getMessage("api.listing.offer.status.rejected", null, "Offer not accepted.", locale);
            return ResponseDto.builder()
                    .data(false)
                    .message(msg)
                    .httpStatus(HttpStatus.BAD_REQUEST.value())
                    .build();
        }
        String msg = messageSource.getMessage("api.listing.offer.error.item_not_found", null, "Item does not exist.", locale);
        return ResponseDto.builder()
                .data(false)
                .message(msg)
                .httpStatus(HttpStatus.NOT_FOUND.value())
                .build();
    }
    @Transactional(readOnly = true)
    public ListPageDto myCatalogFeed(Jwt jwt, String query,int page, int size) {
        if (jwt == null) {
            return ListPageDto.builder().build();
        }
        AppUser currentUser = appUserDetailsService.getAppUserByUsername(jwt.getSubject());
        Long currentUserId = currentUser.getId();
        SellerProfile seller = sellerProfileService.getSellerProfile(currentUserId);

        if(seller==null){
            return ListPageDto.builder()
                    .productList(List.of())
                    .page(0)
                    .pageSize(0)
                    .accumulatedItems(0)
                    .totalPages(0)
                    .totalElements(0)
                    .build();
        }
        Long sellerId = seller.getId();
        Pageable pageable = PageRequest.of(page ==0?page:page-1, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        Specification<ListingTransaction> specification = Specification
                .where(ListingSpecs.hasSellerId(sellerId))
                .and(ListingSpecs.matchesSearch(query));

        Page<ProductListResponse> listings = listingTransactionRepo.findAll(specification, pageable)
                .map(listing -> {
                    Long inventoryId = listing.getInventory().getId();
                    List<MediaResponse> media = mediaService.getImageList(inventoryId);
                    InventoryItemPrice price = inventoryItemPriceService.getPrices(inventoryId);
                  return ListingTransaction.toProductListResponse(listing, media, price);
                });

        return ListPageDto.builder()
                .productList(listings.getContent())
                .page(listings.getNumber())
                .pageSize(listings.getSize())
                .accumulatedItems(getAccumulatedItemsFromPage(listings.getNumber(),listings.getSize(),listings.getTotalElements()))
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
        return listingTransactionRepo.findByListingStatus(ListingStatus.PUBLISHED).stream().map(listing -> {
            Long inventoryId = listing.getInventory() != null ? listing.getInventory().getId() : null;
            List<MediaResponse> media = mediaService.getImageList(inventoryId);
            InventoryItemPrice price = inventoryItemPriceService.getPrices(inventoryId);
            return ListingTransaction.toListTransResponse(listing, media, price);
        }).toList();
    }

    public int getAccumulatedItemsFromPage(int currentPage,int pageSize,long totalElements){
        // 1. Calculate how many items exist up to the start boundary of the current page
        int itemsBeforeCurrentPage = currentPage * pageSize;

        // 2. Compute the exact remaining items left in the database for this page and beyond
        int remainingItems = (int)totalElements - itemsBeforeCurrentPage;

        // 3.  If remaining items are less than page size,
        // use the exact remainder; otherwise, add a full standard page size block.
        int accumulatedTotal;
        if (remainingItems < pageSize) {
            accumulatedTotal = itemsBeforeCurrentPage + remainingItems; // Caps exactly at TotalElements
        } else {
            accumulatedTotal = itemsBeforeCurrentPage + pageSize;       // Adds a standard full page block
        }
        return accumulatedTotal;
    }


    @Transactional(readOnly = true)
    public ProductListResponse getProductById(Jwt jwt,Long listingId) {
        if(jwt==null){
            return ProductListResponse.builder().build();
        }
        ListingTransaction listingTransaction = listingTransactionRepo.findById(listingId).orElse(null);
        if (listingTransaction != null) {
            List<MediaResponse> mediaList = mediaService.getImageList(listingTransaction.getInventory().getId());
            InventoryItemPrice price = inventoryItemPriceService.getPrices(listingTransaction.getInventory().getId());
            BigDecimal serviceCharge = pricingService.getServiceCharge(price.getSellerNewPrice());
            BigDecimal shipmentCost  = shippingOptionService.getItemShippingPrice(listingTransaction.getInventory(),DeliveryOption.HOME_COURIER.name());
            return ListingTransaction.toProductListResponse(listingTransaction, mediaList, price,serviceCharge,shipmentCost);
        }
        return ProductListResponse.builder().build();
    }

    @Transactional(readOnly = true)
    public ProductListResponse getProductById(Long listingId) {
        ListingTransaction listingTransaction = listingTransactionRepo.findById(listingId).orElse(null);
        if (listingTransaction != null) {
            List<MediaResponse> mediaList = mediaService.getImageList(listingTransaction.getInventory().getId());
            InventoryItemPrice price = inventoryItemPriceService.getPrices(listingTransaction.getInventory().getId());
            BigDecimal serviceCharge = pricingService.getServiceCharge(price.getSellerNewPrice());
            BigDecimal shipmentCost  = shippingOptionService.getItemShippingPrice(listingTransaction.getInventory(),DeliveryOption.HOME_COURIER.name());
            return ListingTransaction.toProductListResponse(listingTransaction, mediaList, price,serviceCharge,shipmentCost);
        }
        return ProductListResponse.builder().build();
    }


    @Transactional(readOnly = true)
    public ListTransResponse getProductByListingId(Long listingId) {

        ListingTransaction listingTransaction = listingTransactionRepo.findById(listingId).orElse(null);
        if (listingTransaction != null) {
            List<MediaResponse> mediaList = mediaService.getImageList(listingTransaction.getInventory().getId());
            InventoryItemPrice price = inventoryItemPriceService.getPrices(listingTransaction.getInventory().getId());
            return ListingTransaction.toListTransResponse(listingTransaction, mediaList, price);
        }
        return ListTransResponse.builder().build();
    }

    @Transactional(readOnly = true)
    public ListTransResponse getProductByListingSlug(Long id,String slug) {

        ListingTransaction listingTransaction = listingTransactionRepo.findByIdAndInventoryProductCatalogSlug(id,slug).orElse(null);
        if (listingTransaction != null) {
            List<MediaResponse> mediaList = mediaService.getImageList(listingTransaction.getInventory().getId());
            InventoryItemPrice price = inventoryItemPriceService.getPrices(listingTransaction.getInventory().getId());
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
                        InventoryItemPrice price = inventoryItemPriceService.getPrices(listing.getInventory().getId());
                        List<MediaResponse> mediaList = mediaService.getImageList(listing.getInventory().getId());
                        return ListingTransaction.toListTransResponse(listing, mediaList, price);
                    }).toList();
        }
        return null;
    }


    @Transactional(readOnly = true)
    public List<ListTransResponse> getSellerListings(Long sellerId) {
        if (sellerId != null) {

            return listingTransactionRepo.findAllByInventorySellerId(sellerId).stream()
                    .map(listing -> {
                        InventoryItemPrice price = inventoryItemPriceService.getPrices(listing.getInventory().getId());
                        List<MediaResponse> mediaList = mediaService.getImageList(listing.getInventory().getId());
                        return ListingTransaction.toListTransResponse(listing, mediaList, price);
                    }).toList();
        }
        return List.of(ListTransResponse.builder().build());
    }


    @Transactional
    public void deleteListing(Jwt jwt, Long listing) {
        Locale locale = LocaleContextHolder.getLocale();
        if (jwt != null) {
            listingTransactionRepo.findById(listing).ifPresent(item -> {
                InventoryItem inventoryItem = item.getInventory();
                if (inventoryItem.getStatus().equals(InventoryStatus.RESERVED)) {
                    throw new InvalidOperationException(
                            messageSource.getMessage("api.listing.delete.reserved", null, "Product has been reserved.", locale)
                    );
                }
                if (inventoryItem.getStatus().equals(InventoryStatus.SOLD)) {
                    throw new InvalidOperationException(
                            messageSource.getMessage("api.listing.delete.sold", null, "Product already sold.", locale)
                    );
                }
                listingTransactionRepo.delete(item);
                inventoryItemPriceService.deleteInventoryPrice(inventoryItem);
                try {
                    mediaService.deleteAllByInventoryItem(inventoryItem);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                inventoryItem.setProductCatalog(null);
                inventoryItemService.deleteInventory(inventoryItem);
            });
        }
    }
    @Transactional(readOnly = true)
    public ListingTransaction recentListing(Jwt jwt) {
        if (jwt == null) {
            return null;
        }
        AppUser currentUser = appUserDetailsService.getAppUserByUsername(jwt.getSubject());
        SellerProfile seller = sellerProfileService.getSellerProfile(currentUser.getId());
        if (seller != null) {
            return listingTransactionRepo.findFirstByInventorySellerIdAndListingStatusOrderByCreatedAtDesc(seller.getId(), ListingStatus.PUBLISHED)
                    .orElse(null);
        }
        return null;
    }
}
