package com.pages.service;

import com.pages.dto.*;
import com.pages.enums.ItemSize;
import com.pages.enums.ShipmentConfirmationStatus;
import com.pages.enums.ShipmentStatus;
import com.pages.model.*;
import com.pages.repository.ReturnShipmentRepo;
import com.pages.repository.SellerShipmentRepo;
import com.pages.repository.SellerShipmentTokenRepo;
import com.pages.util.UtilService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShipmentService {

    private final SellerShipmentRepo sellerShipmentRepo;
    private final AppUserDetailsService appUserDetailsService;
    private final SellerProfileService sellerProfileService;
    private final EmailNotificationService emailNotificationService;
    private final ListingOrderService listingOrderService;
    private final ReturnShipmentRepo returnShipmentRepo;
    private final MessageSource messageSource;
    private final DpdShipmentService dpdShipmentService;
    private final PackageConfigurationService packageConfigurationService;
    private final SellerShipmentTokenRepo sellerShipmentTokenRepo;
    private final OrderReturnService orderReturnService;


    public SellerShipment findOrCreateShipment(ListingOrderItem listingOrderItem) {
        return sellerShipmentRepo.findByListingOrderItemId(listingOrderItem.getId())
                .orElseGet(() -> {
                    SellerShipment shipment = SellerShipment.builder()
                            .listingOrderItem(listingOrderItem)
                            .seller(listingOrderItem.getSeller())
                            .deliveredAt(null)
                            .itemSize(listingOrderItem.getInventoryItem().getItemSize())
                            .shippingAddress(listingOrderItem.getListingOrder().getShippingAddress())
                            .shipmentStatus(ShipmentStatus.CREATED)
                            .shippedAt(null)
                            .build();
                    return sellerShipmentRepo.save(shipment);
                });
    }



    @Transactional
    public void createShipment(List<ListingOrderItem> listingOrderItem) {
        listingOrderItem.forEach(item -> {
            SellerShipment shipment = SellerShipment.builder()
                    .listingOrderItem(item)
                    .seller(item.getSeller())
                    .deliveredAt(null)
                    .itemSize(item.getInventoryItem().getItemSize())
                    .shippingAddress(item.getListingOrder().getShippingAddress())
                    .shipmentStatus(ShipmentStatus.AWAITING_SHIPMENT)
                    .shippedAt(null)
                    .build();
            sellerShipmentRepo.save(shipment);
        });
    }

    @Transactional
    public void createReturnShipment(OrderDispute orderDispute) {
       ReturnShipment shipment = returnShipmentRepo.findByOrderDispute(orderDispute)
               .orElse(null);
       if(shipment!=null){
           log.info("Creating shipping for return item: dispute id:{}",orderDispute.getId());

           SellerShipment sellerShipment = sellerShipmentRepo.findByListingOrderItemId(orderDispute.getListingOrderItem().getId()).orElse(null);
           String sellerAddress =    sellerShipment!=null? sellerShipment.getCollectionAddress():null;

           ReturnShipment itemShipment= ReturnShipment.builder()
                   .orderDispute(orderDispute)
                   .itemSize(orderDispute.getListingOrderItem().getItemSize())
                   .sellerReturnAddress(sellerAddress)
                   .buyerAddress(orderDispute.getListingOrderItem().getListingOrder().getShippingAddress())
                   .shipmentStatus(ShipmentStatus.AWAITING_SHIPMENT)
                   .shippedAt(null)
                   .build();
          returnShipmentRepo.save(itemShipment);
       }


    }

    @Transactional(readOnly = true)
    private ListPageShipment sellerShipments(Jwt jwt, Integer page, Integer size) {
        if (jwt != null) {
            AppUser appUser = appUserDetailsService.getAppUserByUsername(jwt.getSubject());
            SellerProfile seller = sellerProfileService.getSellerProfile(appUser.getId());

            Pageable pageable = PageRequest.of(
                    page == 0 ? page : page - 1,
                    size,
                    Sort.by(Sort.Direction.DESC, "createdAt")
            );

            Page<ShipmentResponse> shipments = sellerShipmentRepo.findBySeller(seller, pageable)
                    .map(shipment ->

                            ShipmentResponse.builder()
                            .id(shipment.getId())
                            .orderNumber(shipment.getListingOrderItem().getListingOrder().getOrderNumber())
                            .deliveredAt(shipment.getDeliveredAt())
                            .deliveryAddress(shipment.getShippingAddress())
                            .listingOrderId(shipment.getListingOrderItem().getListingId())
                            .seller("Private person")
                            .confirmationStatus(shipment.getSellerShipmentToken().getShipmentConfirmationStatus()!=null?shipment.getSellerShipmentToken().getShipmentConfirmationStatus().name():null)
                            .shippedAt(shipment.getShippedAt())
                            .trackingNumber(shipment.getTrackingNumber())
                            .itemSize(shipment.getItemSize().name())
                            .status(shipment.getShipmentStatus())
                            .build());

            return ListPageShipment.builder()
                    .shipments(shipments.getContent())
                    .totalPages(shipments.getTotalPages())
                    .page(shipments.getNumber())
                    .totalElements(shipments.getTotalElements())
                    .pageSize(shipments.getSize())
                    .build();
        }
        return ListPageShipment.builder().build();
    }

    @Transactional(readOnly = true)
    private ListPageShipment buyerShipments(Jwt jwt, Integer page, Integer size) {
        if (jwt != null) {
            AppUser appUser = appUserDetailsService.getAppUserByUsername(jwt.getSubject());

            Pageable pageable = PageRequest.of(
                    page == 0 ? page : page - 1,
                    size,
                    Sort.by(Sort.Direction.DESC, "createdAt")
            );

            Page<ShipmentResponse> shipments = returnShipmentRepo.findByOrderDisputeListingOrderItemListingOrderBuyer(appUser, pageable)
                    .map(shipment -> {
                        SellerProfile sellerProfile = shipment.getOrderDispute().getListingOrderItem().getSeller();

                        //String fallbackSellerName = sellerProfile.getUser().getFirstName() + " " + sellerProfile.getUser().getLastName();

                        return ShipmentResponse.builder()
                                .id(shipment.getId())
                                .orderNumber(shipment.getOrderDispute().getListingOrderItem().getListingOrder().getOrderNumber())
                                .deliveredAt(shipment.getDeliveredAt())
                                .deliveryAddress(shipment.getSellerReturnAddress())
                                .listingOrderId(shipment.getOrderDispute().getListingOrderItem().getId())
                                .seller("Private person")
                                .itemSize(shipment.getItemSize())
                                .shippedAt(shipment.getShippedAt())
                                .trackingNumber(shipment.getTrackingNumber())
                                .status(shipment.getShipmentStatus())
                                .build();
                    });

            return ListPageShipment.builder()
                    .shipments(shipments.getContent())
                    .totalPages(shipments.getTotalPages())
                    .page(shipments.getNumber())
                    .totalElements(shipments.getTotalElements())
                    .pageSize(shipments.getSize())
                    .build();
        }
        return ListPageShipment.builder().build();
    }

    public ShipmentStatus shipmentStatus(ListingOrderItem listingOrderItem) {
        return sellerShipmentRepo.findByListingOrderItemId(listingOrderItem.getId()).map(SellerShipment::getShipmentStatus).orElse(null);
    }

    public SellerShipment shipment(ListingOrderItem listingOrderItem) {
        return sellerShipmentRepo.findByListingOrderItemId(listingOrderItem.getId()).orElse(null);
    }

    private ShipmentStatus getShippingStatus(String status) {
        String cleanStatus = status.trim().toUpperCase();
        return switch (cleanStatus) {
            case "AWAITING_SHIPMENT" -> ShipmentStatus.AWAITING_SHIPMENT;
            case "SHIPPED" -> ShipmentStatus.SHIPPED;
            case "DELIVERED" -> ShipmentStatus.DELIVERED;
            case "RETURNED" -> ShipmentStatus.RETURNED;
            default -> ShipmentStatus.CREATED;
        };
    }

    private String getMessage(String code) {
        return messageSource.getMessage(
                code,
                null,
                LocaleContextHolder.getLocale()
        );
    }

    @Transactional
    public void updateShippingStatus(Jwt jwt, ShipmentRequest request) {
        if (jwt == null) {

            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,getMessage("api_errors.shipment.unauthorized"));
        }
        AppUser appUser = appUserDetailsService.getAppUserByUsername(jwt.getSubject());

        SellerShipment shipment = sellerShipmentRepo
                .findById(request.getShipmentId())
                .orElse(null);


        if (shipment == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,getMessage("api_errors.shipment.not_found"));
        }
        // Only the seller who owns this shipment or an admin may update it.

        boolean isSeller = appUser.getId().equals(
                shipment.getSeller().getUser().getId()
        );

        boolean isAdmin = appUser.getUserRoles().contains("ROLE_ADMIN");

        if (!isSeller && !isAdmin) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    getMessage("api_errors.shipment.unauthorized")
            );
        }

        if( shipment.getSellerShipmentToken().getShipmentConfirmationStatus()!= ShipmentConfirmationStatus.CONFIRMED){
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,getMessage("api_errors.shipment.seller.not_confirmed"));
        }

        if (shipment.getShipmentStatus() == ShipmentStatus.CANCELLED) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    getMessage("api_errors.shipment.status.cancelled")
            );
        }

        Instant requestedActionDate = request.getCreatedAt() != null
                ? request.getCreatedAt()
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                : null;

        String status = request.getStatus();

        if (("SHIPPED".equalsIgnoreCase(status)
                || "DELIVERED".equalsIgnoreCase(status))
                && requestedActionDate == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    getMessage("api_errors.shipment.validation.missing_date")
            );
        }

        if ("SHIPPED".equalsIgnoreCase(status) && (requestedActionDate!=null && requestedActionDate.isBefore(shipment.getCreatedAt().minus(1,ChronoUnit.DAYS)))) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    getMessage("api_responses.shipment.shipment.date.error")
            );
        }else{
            shipment.setShippedAt(requestedActionDate);
        }

        if ("DELIVERED".equalsIgnoreCase(status)) {
            Instant existingShippedAt = shipment.getShippedAt();

            if (existingShippedAt == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        getMessage("api_errors.shipment.validation.not_shipped_yet")
                );
            }

            if (requestedActionDate != null && requestedActionDate.isBefore(existingShippedAt.minus(1,ChronoUnit.DAYS))) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        getMessage(
                                "api_errors.shipment.validation.invalid_delivery_sequence"
                        )
                );
            }

            shipment.setDeliveredAt(requestedActionDate);
        }

        if ("RETURNED".equalsIgnoreCase(status)) {
            shipment.setDeliveredAt(
                    requestedActionDate != null
                            ? requestedActionDate
                            : Instant.now()
            );
        }

        shipment.setShipmentStatus(getShippingStatus(status));
        shipment.setComment(request.getComment());
        shipment.setTrackingNumber(request.getTrackingNumber());

        SellerShipment sellerShipment = sellerShipmentRepo.save(shipment);
        ListingOrderItem listingOrderItem = sellerShipment.getListingOrderItem();

        String token = "";

        if ( "DELIVERED".equalsIgnoreCase(status)) {

            token = UtilService.generateReceiptConfirmationToken();

            listingOrderItem.setReceiptConfirmationToken(token);
            listingOrderItem.setReceiptConfirmationTokenExpiresAt(
                    Instant.now().plus(3, ChronoUnit.DAYS)
            );
            listingOrderItem.setReceiptConfirmedAt(null);

            listingOrderService.save(listingOrderItem);
        }

        ListingOrder listingOrder = listingOrderItem.getListingOrder();
        AppUser buyer = appUserDetailsService.getAppUserId(
                listingOrder.getBuyer().getId()
        );

        String buyerName = buyer.getFirstName() + " " + buyer.getLastName();
        String product = listingOrderItem.getInventoryItem()
                .getProductCatalog()
                .getName();


        emailNotificationService.sendShipmentStatusToBuyer(
                buyer.getUsername(),
                buyerName,
                listingOrder.getOrderNumber(),
                product,
                sellerShipment.getShipmentStatus().name(),
                sellerShipment.getTrackingNumber(),
                "DPD",
                token
        );

    }

    /**
     * AUTOMATED RETURN COURIER WEBHOOK RECEIVER
     * Updates real-time parcel tracks on your Railway persistent volume data records.
     * Automatically triggers the final platform ledger clawbacks when the item lands back with the seller.
     */
    @Transactional
    public ResponseDto<Boolean> updateReturnShippingStatus(Jwt jwt, ShipmentRequest request) {
        if (jwt == null) {
            return ResponseDto.<Boolean>builder()
                    .data(false)
                    .message(getMessage("api_errors.shipment.unauthorized"))
                    .httpStatus(HttpStatus.UNAUTHORIZED.value())
                    .build();
        }

        ReturnShipment shipment = returnShipmentRepo.findById(request.getShipmentId()).orElse(null);

        if (shipment == null) {
            return ResponseDto.<Boolean>builder()
                    .data(false)
                    .message(getMessage("api_errors.shipment.not_found"))
                    .httpStatus(HttpStatus.NOT_FOUND.value())
                    .build();
        }

        Instant requestedActionDate = request.getCreatedAt() != null
                ? request.getCreatedAt().atStartOfDay(ZoneId.systemDefault()).toInstant()
                : null;

        String status = request.getStatus();

        // Strict date validation guardrails
        if (("SHIPPED".equalsIgnoreCase(status) || "DELIVERED".equalsIgnoreCase(status) || "RETURNED".equalsIgnoreCase(status))
                && requestedActionDate == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    getMessage("api_errors.shipment.validation.missing_date")
            );
        }


        if ("SHIPPED".equalsIgnoreCase(status)) {
            shipment.setShippedAt(requestedActionDate);
        }

        //  Natively evaluates both "DELIVERED" and "RETURNED" webhooks cleanly
        if ("DELIVERED".equalsIgnoreCase(status) || "RETURNED".equalsIgnoreCase(status)) {
            Instant existingShippedAt = shipment.getShippedAt();

            if (existingShippedAt == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        getMessage("api_errors.shipment.validation.not_shipped_yet")
                );
            }

            if (requestedActionDate != null && requestedActionDate.isBefore(existingShippedAt)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        getMessage("api_errors.shipment.validation.invalid_delivery_sequence")
                );
            }

            //  Map to your correct, explicit ReturnShipment property column!
            shipment.setDeliveredAt(requestedActionDate != null ? requestedActionDate : Instant.now());
            String token = UtilService.generateShipmentToken().getToken();
            shipment.setReceiptConfirmationToken(token);
            shipment.setReceiptConfirmationTokenExpiresAt(Instant.now().plus(3,ChronoUnit.DAYS));
            returnShipmentRepo.save(shipment);
            sendNotification(shipment,token);
        }

        // Map and save core metadata properties safely
        shipment.setShipmentStatus(getShippingStatus(status));
        shipment.setTrackingNumber(request.getTrackingNumber());
        // Note: Assure your ReturnShipment entity supports custom comment variables if requested!

         returnShipmentRepo.save(shipment);
        returnShipmentRepo.flush();

        return ResponseDto.<Boolean>builder()
                .data(true)
                .message(getMessage("api_responses.shipment.updated_success"))
                .httpStatus(HttpStatus.OK.value())
                .build();
    }



    private void sendNotification(ReturnShipment shipment, String token) {
        if (shipment == null) return;

        // STEP 1: DEFENSIVE EXTRACTION LAYER
        OrderDispute returnOrder = shipment.getOrderDispute();
        if (returnOrder == null) {
            log.error("Notification Shield: Aborting dispatch. OrderReturn relation missing for Shipment ID: {}", shipment.getId());
            return;
        }

        ListingOrderItem listingOrderItem = returnOrder.getListingOrderItem();
        if (listingOrderItem == null) {
            log.error("Notification Shield: Aborting dispatch. ListingOrderItem snapshot missing for Return ID: {}", returnOrder.getId());
            return;
        }

        ListingOrder order = listingOrderItem.getListingOrder();
        SellerProfile sellerProfile = listingOrderItem.getSeller();

        // STEP 2:  EXTRACT CORRECT ADDRESS CHANNELS
        // Seller details (Receiving the returned box)
        String sellerEmail = (sellerProfile != null && sellerProfile.getUser() != null) ? sellerProfile.getUser().getUsername() : null;
        String sellerName = sellerProfile != null ? sellerProfile.getFullLegalName() : "Użytkownik Kasoa";


        String orderNumber = order != null ? order.getOrderNumber() : "000000";
        String productName = listingOrderItem.getProductNameSnapshot() != null ? listingOrderItem.getProductNameSnapshot() : "Produkt";

        // Pull dynamically from your enum mapping fields instead of hardcoding "DPD"!
        String activeCarrierMethod = shipment.getCarrier() != null ? shipment.getCarrier() : "PRZESYŁKA_MANUALNA";
        String trackingCodeStr = shipment.getTrackingNumber() != null ? shipment.getTrackingNumber() : "BRAK_NUMERU";
        String currentStatusStr = shipment.getShipmentStatus() != null ? shipment.getShipmentStatus().name() : "PENDING";

        log.info("Notification Shield: Preparing dual dispatch routes for Return Tracking Ref: #{}", trackingCodeStr);

        //  STEP 3: DISPATCH EMAIL METRICS TO THE CORRECT CHANNELS

        // Route A: Alert the Seller that their manual confirmation link token is ready for inspection
        if (sellerEmail != null && token != null) {
            log.info("Notification Shield: Routing signed manual verification token link to Seller: {}", sellerEmail);

            // Reuses your async Resend service engine to push your new polymorphic link url tracking parameters
            emailNotificationService.sendShipmentStatusToSeller(
                    sellerEmail,
                    sellerName,
                    orderNumber,
                    productName,
                    currentStatusStr,
                    trackingCodeStr,
                    activeCarrierMethod,
                    token
            );
        }

    }


    /**
     * Confirms the shipment delivery and triggers the associated financial refund to buyer.
     * Enforced with a database transaction to prevent race conditions.
     */
    @Transactional
    public Boolean validateAndProcessDelivery(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }

        // 1. Fetch with a Pessimistic Lock to prevent concurrent double-processing
        ReturnShipment orderItem = returnShipmentRepo.findByReceiptConfirmationToken(token)
                .orElse(null);

        if (orderItem == null) {
            return null;
        }

        Instant now = Instant.now();

        // 2. Token expiration verification
        if (orderItem.getReceiptConfirmationTokenExpiresAt() == null
                || orderItem.getReceiptConfirmationTokenExpiresAt().isBefore(now)) {
            return null;
        }

        // 3. Idempotency validation (Already confirmed check)
        if (orderItem.getReceiptConfirmedAt() != null) {
            return null;
        }

        // 4. Update the shipment status states immediately
        orderItem.setReceiptConfirmedAt(now);
        orderItem.setReceiptConfirmationTokenExpiresAt(now); // Invalidates instantly
        // Optional safety: orderItem.setReceiptConfirmationToken(null);
        // 5. Persist the state transition to the database
        returnShipmentRepo.save(orderItem);
        return true;

    }

    /**
     * check that item return is not already confirmed when seller reuses the link
     * @param token
     * @return
     */
    public boolean isItemReturnConfirmed(String token) {

     ReturnShipment orderItem=  returnShipmentRepo.findByReceiptConfirmationToken(token).orElse(null);

        if (orderItem == null) {
            return false;
        }
        // Already confirmed
        return orderItem.getReceiptConfirmedAt() != null;
    }

    @Transactional(readOnly = true)
    private ListPageShipment allShipments(
            Jwt jwt,
            Integer page,
            Integer size
    ) {
        if (jwt != null) {
            Pageable pageable = PageRequest.of(
                    page == 0 ? page : page - 1,
                    size,
                    Sort.by(Sort.Direction.DESC, "createdAt")
            );

            Page<ShipmentResponse> shipments = sellerShipmentRepo
                    .findAll(pageable)
                    .map(shipment -> {

                        String confirmation = shipment.getSellerShipmentToken().getShipmentConfirmationStatus()!=null?
                                shipment.getSellerShipmentToken().getShipmentConfirmationStatus().name():null;

                         return    ShipmentResponse.builder()
                                        .id(shipment.getId())
                                        .deliveredAt(shipment.getDeliveredAt())
                                        .deliveryAddress(shipment.getShippingAddress())
                                        .listingOrderId(
                                                shipment.getListingOrderItem().getListingId()
                                        )
                                        .seller(
                                                shipment.getSeller().getUser().getFirstName()
                                                        + " "
                                                        + shipment.getSeller().getUser().getLastName()
                                        )
                                        .shippedAt(shipment.getShippedAt())
                                        .itemSize(shipment.getItemSize().name())
                                        .trackingNumber(shipment.getTrackingNumber())
                                        .status(shipment.getShipmentStatus())
                                 .confirmationStatus(confirmation)
                                        .orderNumber(
                                                shipment.getListingOrderItem()
                                                        .getListingOrder()
                                                        .getOrderNumber()
                                        )
                                        .build();
                            }

                            );

            return ListPageShipment.builder()
                    .shipments(shipments.getContent())
                    .totalPages(shipments.getTotalPages())
                    .page(shipments.getNumber())
                    .totalElements(shipments.getTotalElements())
                    .pageSize(shipments.getSize())
                    .build();
        }

        return ListPageShipment.builder().build();
    }

    @Transactional(readOnly = true)
    private ListPageShipment allReturnShipments(
            Jwt jwt,
            Integer page,
            Integer size
    ) {
        if (jwt != null) {
            Pageable pageable = PageRequest.of(
                    page == 0 ? page : page - 1,
                    size,
                    Sort.by(Sort.Direction.DESC, "createdAt")
            );

            Page<ShipmentResponse> shipments = returnShipmentRepo
                    .findAll(pageable)
                    .map(shipment -> {
                        SellerProfile seller = shipment
                                .getOrderDispute()
                                .getListingOrderItem()
                                .getSeller();

                        String sellerName = seller.getUser().getFirstName()
                                + " "
                                + seller.getUser().getLastName();

                        return ShipmentResponse.builder()
                                .id(shipment.getId())
                                .deliveredAt(shipment.getDeliveredAt())
                                .deliveryAddress(shipment.getSellerReturnAddress())
                                .listingOrderId(
                                        shipment.getOrderDispute()
                                                .getListingOrderItem()
                                                .getListingId()
                                )
                                .seller(sellerName)
                                .shippedAt(shipment.getShippedAt())
                                .trackingNumber(shipment.getTrackingNumber())
                                .status(shipment.getShipmentStatus())
                                .itemSize(shipment.getItemSize())
                                .orderNumber(
                                        shipment.getOrderDispute()
                                                .getListingOrderItem()
                                                .getListingOrder()
                                                .getOrderNumber()
                                )
                                .build();
                    });

            return ListPageShipment.builder()
                    .shipments(shipments.getContent())
                    .totalPages(shipments.getTotalPages())
                    .page(shipments.getNumber())
                    .totalElements(shipments.getTotalElements())
                    .pageSize(shipments.getSize())
                    .build();
        }

        return ListPageShipment.builder().build();
    }


    @Transactional(readOnly = true)
    public ListPageShipment actualShipments(
            Jwt jwt,
            Integer page,
            Integer size
    ) {
        if (jwt != null) {
            List<String> authorities =
                    jwt.getClaimAsStringList("authorities");

            if (authorities == null) {
                authorities = jwt.getClaimAsStringList("ROLE");
            }

            boolean isAdmin = authorities != null
                    && authorities.contains("ROLE_ADMIN");

            if (isAdmin) {
                return allShipments(jwt, page, size);
            }

            return sellerShipments(jwt, page, size);
        }

        return ListPageShipment.builder().build();
    }

    @Transactional(readOnly = true)
    public ListPageShipment returnShipments(
            Jwt jwt,
            Integer page,
            Integer size
    ) {
        if (jwt != null) {
            List<String> authorities =
                    jwt.getClaimAsStringList("authorities");

            if (authorities == null) {
                authorities = jwt.getClaimAsStringList("ROLE");
            }

            boolean isAdmin = authorities != null
                    && authorities.contains("ROLE_ADMIN");

            if (isAdmin) {
                return allReturnShipments(jwt, page, size);
            }

            return buyerShipments(jwt, page, size);
        }

        return ListPageShipment.builder().build();
    }

    @Transactional(readOnly = true)
    public Long myShipments(Jwt jwt) {
        if (jwt != null) {
            AppUser appUser =
                    appUserDetailsService.getAppUserByUsername(jwt.getSubject());

            SellerProfile seller =
                    sellerProfileService.getSellerProfile(appUser.getId());

            return sellerShipmentRepo.findBySeller(seller)
                    .stream()
                    .map(SellerShipment::getShipmentStatus)
                    .filter(status -> status == ShipmentStatus.AWAITING_SHIPMENT)
                    .count();
        }

        return 0L;
    }

    @Transactional(readOnly = true)
    public SellerShipment recentShipment(Jwt jwt) {
        if (jwt != null) {
            AppUser appUser =
                    appUserDetailsService.getAppUserByUsername(jwt.getSubject());

            SellerProfile seller =
                    sellerProfileService.getSellerProfile(appUser.getId());

            if (seller != null) {
                return sellerShipmentRepo
                        .findFirstBySellerIdOrderByCreatedAtDesc(seller.getId())
                        .orElse(null);
            }
        }

        return null;
    }

    public ShipmentStatus getShipmentStatus(String orderNumber) {
        return sellerShipmentRepo
                .findByListingOrderItemListingOrderOrderNumber(orderNumber)
                .map(SellerShipment::getShipmentStatus)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public ResponseDto<Object> getShipment(String token){

       SellerShipmentToken shipmentToken = sellerShipmentTokenRepo.findByToken(token).orElse(null);
       if(shipmentToken!=null && shipmentToken.getShipmentConfirmationStatus()==ShipmentConfirmationStatus.CONFIRMED){
           return ResponseDto.builder()
                   .httpStatus(HttpStatus.OK.value())
                   .data(shipmentToken.getShipmentConfirmationStatus())
                   .message("Shipment already confirmed")
                   .build();
       }
     List<ShipmentItemResponse>  items = sellerShipmentRepo.findBySellerShipmentTokenToken(token).stream()
             .map(shipment -> {

               return ShipmentItemResponse.builder()
                         .shipmentId(shipment.getId())
                         .productName(shipment.getListingOrderItem().getProductNameSnapshot())
                        .orderNumber(shipment.getListingOrderItem().getListingOrder().getOrderNumber())
                         .build();
             }).toList();
       return ResponseDto.builder()
               .data(items)
               .httpStatus(HttpStatus.OK.value())
               .build();
    }

    @Transactional(readOnly = true)
    public List<SellerShipment> getSellerShipments(String token){
        return   sellerShipmentRepo.findBySellerShipmentTokenToken(token);
    }



    @Transactional(readOnly = true)
    public SellerShipment getShipments(Long orderItemId){
        return   sellerShipmentRepo.findByListingOrderItemId(orderItemId).orElse(null);
    }


    @Transactional
    public void updateAndOrderCourierConfirmation(ShipmentConfirmationRequest request) {

        try{
            // 1. Guard clause for malformed or empty payloads
            if (request == null || request.getShipmentIds() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,getMessage("Provide necessary information"));
            }

            String token = request.getToken();

            SellerShipmentToken sellerShipmentToken= sellerShipmentTokenRepo.findByTokenForUpdate(token)
                    .orElseThrow(()->new ResponseStatusException(HttpStatus.BAD_REQUEST,getMessage("No associated token for this shipment")));

            if(sellerShipmentToken.getExpiresAt().isBefore(Instant.now()) || sellerShipmentToken.getUsedAt()!=null){
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,getMessage("Token has expired or been already used"));
            }
            //confirm token usage to prevent reuse
            sellerShipmentToken.setUsedAt(Instant.now());
            sellerShipmentToken.setShipmentConfirmationStatus(ShipmentConfirmationStatus.CONFIRMED);
            sellerShipmentTokenRepo.save(sellerShipmentToken);

            // 2.Construct the immutable string ONCE outside the loop
            var pickup = request.getPickupAddress();
            String sellerAddress = String.join(", ",
                    request.getName(),
                    pickup.getStreet(),
                    pickup.getPostalCode(),
                    pickup.getCity(),
                    pickup.getCountry()
            );

            // 3.  Fetch all shipments in a SINGLE database query round-trip
            List<SellerShipment> itemsToShip =
                    sellerShipmentRepo.findBySellerShipmentTokenToken(request.getToken());

            List<SellerShipment> confirmedShipments =
                    sellerShipmentRepo.findAllById(request.getShipmentIds());

            Set<Long> confirmedIds = confirmedShipments.stream()
                    .map(SellerShipment::getId)
                    .collect(Collectors.toSet());

            // orders to cancel because seller did not confirm
            List<SellerShipment> itemsToCancel = itemsToShip.stream()
                    .filter(item -> !confirmedIds.contains(item.getId()))
                    .toList();


            // 4. Update the entities in memory
            for (SellerShipment itemToShip : confirmedShipments) {
                itemToShip.setCollectionAddress(sellerAddress);
                itemToShip.setSellerComment(request.getComment());
                sellerShipmentRepo.save(itemToShip);
            }

            //cancel unfulfilled orders
            /*
            itemsToCancel.stream().map(SellerShipment::getListingOrderItem).forEach(item->{
                orderReturnService.executeOrderPartialCancellation(item.getId());
            });

             */
        }catch (Exception e){
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,e.getMessage());
        }
    }


}
