package com.pages.service;

import com.pages.enums.ShipmentStatus;
import com.pages.model.*;
import com.pages.repository.SellerShipmentRepo;
import com.pages.repository.SellerShipmentTokenRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@Transactional // 🚀 ATOMIC PROTECTION: Secures complete data rollbacks across tables if any operation fails
public class SellerShipmentTokenService {

    private final SellerShipmentTokenRepo sellerShipmentTokenRepo;
    private final SellerShipmentRepo sellerShipmentRepo;
    private final MessageSource messageSource;

    public SellerShipmentTokenService(SellerShipmentTokenRepo sellerShipmentTokenRepo,
                                      SellerShipmentRepo sellerShipmentRepo,
                                      MessageSource messageSource) {
        this.sellerShipmentTokenRepo = sellerShipmentTokenRepo;
        this.sellerShipmentRepo = sellerShipmentRepo;
        this.messageSource = messageSource;
    }

    /**
     * Helper method to dynamically extract localized properties out of resources bundles.
     */
    private String getMessage(String code) {
        return messageSource.getMessage(
                code,
                null,
                LocaleContextHolder.getLocale()
        );
    }

    /**
     * Creates one shipment-action token for a seller/order
     * and assigns it strictly to the targeted active shipment rows belonging to that envelope.
     */
    public void createRecord(SellerProfile seller, SellerShipmentToken tokenInput, ListingOrder order) {
        if (seller == null || tokenInput == null || order == null) {
            throw new IllegalArgumentException("Parametry wejściowe metody tworzenia tokenu nie mogą być puste.");
        }

        //  Narrow query parameters to isolate ONLY rows matching this active checkout
        List<SellerShipment> shipments = sellerShipmentRepo
                .findBySellerAndListingOrderItemListingOrder(seller, order);

        //  Handle empty database queries dynamically via bundle
        if (shipments.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, getMessage("api_errors.shipment_token.no_items"));
        }

        // Persist token safely into its own isolated tracking variable frame
        SellerShipmentToken savedToken = sellerShipmentTokenRepo.save(tokenInput);

        for (SellerShipment shipment : shipments) {

            //Enforce direct owner verification guards dynamically
            if (!shipment.getSeller().getId().equals(seller.getId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, getMessage("api_errors.shipment_token.seller_mismatch"));
            }

            // Enforce order sequence structural alignment checks dynamically
            if (!shipment.getListingOrderItem()
                    .getListingOrder()
                    .getId()
                    .equals(order.getId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, getMessage("api_errors.shipment_token.order_mismatch"));
            }

            shipment.setSellerShipmentToken(savedToken);
        }

        sellerShipmentRepo.saveAll(shipments);
        log.info("Successfully bound verification shipment token ID #{} to {} order items.",
                savedToken.getId(), shipments.size());
    }

    /**
     * Validates the token used by the seller's horizontal email confirmation link layout views.
     */
    @Transactional(readOnly = true)
    public SellerShipmentToken validateToken(String tokenValue) {
        if (tokenValue == null || tokenValue.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, getMessage("api_errors.shipment_token.invalid"));
        }

        //  Handle completely missing or scrambled string values natively via bundle
        SellerShipmentToken token = sellerShipmentTokenRepo
                .findByToken(tokenValue.trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, getMessage("api_errors.shipment_token.invalid")));

        //  Block reusable link exploitation vulnerabilities natively via bundle
        if (token.getUsedAt() != null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, getMessage("api_errors.shipment_token.already_used"));
        }

        //  validation checks for chronologically stale parameters via bundle
        if (token.getExpiresAt().isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, getMessage("api_errors.shipment_token.expired"));
        }

        return token;
    }
}
