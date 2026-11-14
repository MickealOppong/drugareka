package com.pages.service;

import com.pages.dto.*;
import com.pages.enums.ListingStatus;
import com.pages.enums.SellerStatus;
import com.pages.exception.EntityNotFoundException;
import com.pages.exception.InvalidOperationException;
import com.pages.model.*;
import com.pages.repository.SellerPayoutRepo;
import com.pages.repository.SellerProfileRepo;
import com.pages.specs.SellerProfileSpec;
import com.pages.specs.SellerShipmentSpecs;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class SellerProfileService {

    // PLATFORM PARAMETER CONSTANTS: Legal DAC7 threshold boundaries for EU consumer markets
    private static final int DAC7_MAX_SALES_COUNT = 30;
    private static final BigDecimal DAC7_MAX_GROSS_VOLUME_PLN = new BigDecimal("8600.00"); // Approx €2,000 equivalent


    private final SellerProfileRepo sellerProfileRepo;
    private final AppUserDetailsService appUserDetailsService;
    private final MessageSource messageSource;


    private SellerStatus getSellerStatus(String status) {
        String cleanStatus = status.trim().toUpperCase();
        return switch (cleanStatus) {
            case "ACTIVE" -> SellerStatus.ACTIVE;
            case "BLOCKED" -> SellerStatus.BLOCKED;
            case "PENDING" -> SellerStatus.PENDING;
            case "SUSPENDED" -> SellerStatus.SUSPENDED;
            default -> throw new IllegalArgumentException("Unknown seller status"+cleanStatus);
        };
    }

    public SellerProfile getSeller(Long id){
       return sellerProfileRepo.findById(id).orElse(null);
    }

    public SellerProfile findOrCreateSellerProfile(SellerProfileDto dto){
              return    sellerProfileRepo.findByUser(dto.getUser())
                         .orElseGet(()->{
                     SellerProfile profile = new SellerProfile(dto);
                     return sellerProfileRepo.save(profile);
                 });
    }

    public String getStripeAccountId(Jwt jwt){
        if(jwt==null){
            return null;
        }
        AppUser appUser =appUserDetailsService.getAppUserByUsername(jwt.getSubject());
        if ((appUser==null)){
            return null;
        }
        return sellerProfileRepo.findByUserId(appUser.getId()).map(SellerProfile::getStripeAccountId)
                .orElse(null);
    }

    @Transactional
    public boolean changeStatus(SellerProfileUpdate dto) {
        try {
            SellerProfile seller = sellerProfileRepo.findById(dto.getSellerId())
                    .orElse(null);

            if (seller == null) {
                log.warn("Seller profile not found for seller id: {}", dto.getSellerId());
                return false;
            }

            SellerStatus newStatus = getSellerStatus(dto.getStatus());

            seller.setStatus(newStatus);
            seller.setReason(dto.getReason());

            sellerProfileRepo.save(seller);

            return true;

        } catch (Exception e) {
            log.error(
                    "Update failed for seller id: {}. Reason: {}",
                    dto.getSellerId(),
                    e.getMessage(),
                    e
            );
            return false;
        }
    }

    public SellerProfile getSellerProfile(Long userId){
        return sellerProfileRepo.findByUserId(userId).orElse(null);
    }

    public ListPageSellerProfile getSellers(String query,int page,int size){

        Pageable pageable = PageRequest.of(
                page == 0 ? 0 : page - 1,
                size,
                Sort.by(Sort.Direction.DESC, "createdAt")
        );


        Specification<SellerProfile> specification = Specification
                .where(SellerProfileSpec.matchesSearch(query));

       Page<SellerProfileResponse> sellers = sellerProfileRepo.findAll(specification,pageable)
               .map(sellerProfile -> {
                   return SellerProfileResponse.builder()
                           .sellerId(sellerProfile.getId())
                           .currentYearGrossVolumePln(sellerProfile.getCurrentYearGrossVolumePln())
                           .fullLegalName(sellerProfile.getFullLegalName())
                           .currentYearSalesCount(sellerProfile.getCurrentYearSalesCount())
                           .dac7FlaggedAt(sellerProfile.getDac7FlaggedAt())
                           .status(sellerProfile.getStatus().name())
                           .numberOfCancelledOrders(sellerProfile.getNumberOfCancelledOrders())
                           .identityVerified(sellerProfile.isIdentityVerified())
                           .verifiedAt(sellerProfile.getVerifiedAt())
                           .totalSales(sellerProfile.getTotalSales())
                           .stripeAccountId(sellerProfile.getStripeAccountId())
                           .stripeAccountUpdatedAt(sellerProfile.getStripeAccountUpdatedAt())
                           .stripeChargesEnabled(sellerProfile.isStripeChargesEnabled())
                           .stripePayoutsEnabled(sellerProfile.isStripePayoutsEnabled())
                           .totalSettlement(sellerProfile.getTotalSettlement())
                           .stripeTransfersStatus(sellerProfile.getStripeTransfersStatus())
                           .userId(sellerProfile.getUser().getId())
                           .stripeOnboardingComplete(sellerProfile.isStripeOnboardingComplete())
                           .build();
               });
       return ListPageSellerProfile.builder()
               .sellers(sellers.getContent())
               .accumulatedItems(getAccumulatedItemsFromPage(sellers.getNumber(),sellers.getSize(),sellers.getTotalElements()))
               .page(sellers.getNumber())
               .pageSize(sellers.getSize())
               .build();
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
    /**
     *
     * Aggregates total order lines, resolves multiple seller balances simultaneously without loop thrashing,
     * updates lifetime metrics, and dynamically computes calendar-year DAC7 tax compliance thresholds.
     */

    @Transactional
    public void updateSellerTotalSales(List<ListingOrderItem> orderItems) {
        if (orderItems == null || orderItems.isEmpty()) {
            return;
        }

        try {
            // Aggregate item sales by seller and track distinct orders.
            Map<Long, BigDecimal> sellerOrderTotals = new HashMap<>();
            Map<Long, Set<Long>> sellerOrderIds = new HashMap<>();

            for (ListingOrderItem item : orderItems) {
                if (item == null
                        || item.getSeller() == null
                        || item.getSeller().getId() == null
                        || item.getSellerPrice() == null
                        || item.getListingOrder() == null
                        || item.getListingOrder().getId() == null) {
                    continue;
                }

                Long sellerId = item.getSeller().getId();
                Long orderId = item.getListingOrder().getId();
                BigDecimal itemPrice = item.getSellerPrice();

                // Correctly add every item's price to the seller's total.
                sellerOrderTotals.merge(
                        sellerId,
                        itemPrice,
                        BigDecimal::add
                );

                // Count each order only once per seller within this call.
                sellerOrderIds
                        .computeIfAbsent(sellerId, id -> new HashSet<>())
                        .add(orderId);
            }

            if (sellerOrderTotals.isEmpty()) {
                return;
            }

            List<SellerProfile> profilesToUpdate = new ArrayList<>();

            for (Map.Entry<Long, BigDecimal> entry
                    : sellerOrderTotals.entrySet()) {

                Long sellerId = entry.getKey();
                BigDecimal aggregatedOrderTotal = entry.getValue();

                SellerProfile seller = sellerProfileRepo
                        .findByIdForUpdate(sellerId)
                        .orElseThrow(() ->
                                new InvalidOperationException(
                                        "Seller profile not found for ID: "
                                                + sellerId
                                )
                        );

                int distinctOrderCount = sellerOrderIds
                        .getOrDefault(sellerId, Collections.emptySet())
                        .size();

                log.info(
                        "Updating sales for seller {}: amount={} PLN, distinct orders={}",
                        sellerId,
                        aggregatedOrderTotal,
                        distinctOrderCount
                );

                // Lifetime sales volume.
                BigDecimal lifetimeSales = seller.getTotalSales() != null
                        ? seller.getTotalSales()
                        : BigDecimal.ZERO;

                seller.setTotalSales(
                        lifetimeSales.add(aggregatedOrderTotal)
                );

                // Current calendar-year sales metrics.
                int currentYearCount =
                        seller.getCurrentYearSalesCount() != null
                                ? seller.getCurrentYearSalesCount()
                                : 0;

                BigDecimal currentYearVolume =
                        seller.getCurrentYearGrossVolumePln() != null
                                ? seller.getCurrentYearGrossVolumePln()
                                : BigDecimal.ZERO;

                seller.setCurrentYearSalesCount(
                        currentYearCount + distinctOrderCount
                );

                seller.setCurrentYearGrossVolumePln(
                        currentYearVolume.add(aggregatedOrderTotal)
                );

                // DAC7 tracking threshold checks.
                if (!seller.isDac7Reportable()) {
                    boolean crossedSalesCount =
                            seller.getCurrentYearSalesCount()
                                    >= DAC7_MAX_SALES_COUNT;

                    boolean crossedVolumeLimit =
                            seller.getCurrentYearGrossVolumePln()
                                    .compareTo(DAC7_MAX_GROSS_VOLUME_PLN) >= 0;

                    if (crossedSalesCount || crossedVolumeLimit) {
                        log.warn(
                                "DAC7 review threshold reached for seller {}",
                                sellerId
                        );

                        seller.setDac7Reportable(true);
                        seller.setDac7FlaggedAt(Instant.now());
                    }
                }

                profilesToUpdate.add(seller);
            }

            sellerProfileRepo.saveAll(profilesToUpdate);

        } catch (InvalidOperationException e) {
            log.error(
                    "Failed to update seller sales accounting",
                    e
            );
            throw e;

        } catch (Exception e) {
            log.error(
                    "Unexpected failure updating seller sales accounting",
                    e
            );
            throw new InvalidOperationException(
                    "Could not update seller profiles due to an internal server error."
            );
        }
    }


    @Transactional
    public void updateSellerTotalSales(ListingOrderItem item) {
        if (item == null) {
            return ;
        }

        try {
            // STEP 1: Aggregate order financial splits by Seller ID to prevent multiple database hits

                // Protect arithmetic against uninstantiated null fee fields safely
                BigDecimal itemPrice = item.getSellerPrice() != null ? item.getSellerPrice() : BigDecimal.ZERO;
                BigDecimal shippingCost = item.getShippingCost() != null ? item.getShippingCost() : BigDecimal.ZERO;
                BigDecimal totalLineAmount = itemPrice.add(shippingCost);


            // STEP 2: Process each distinct vendor exactly once
           Long sellerId = item.getSeller().getId();
            SellerProfile seller = sellerProfileRepo.findByIdForUpdate(sellerId)
                    .orElseThrow(() -> new InvalidOperationException("Seller profile not found for user ID: " + sellerId));

            log.info("Financial System: Aggregating order funds for seller profile: {} [Amount: +{} zł]",
                    seller.getUser().getUsername(), totalLineAmount);

            // --- Update Lifetime Accounting Metrics ---
            BigDecimal currentLifetimeSales = seller.getTotalSales() != null ? seller.getTotalSales() : BigDecimal.ZERO;
            seller.setTotalSales(currentLifetimeSales.add(itemPrice));

            // --- Update Calendar-Year Regulatory Counters ---
            int currentYearCount = seller.getCurrentYearSalesCount() != null ? seller.getCurrentYearSalesCount() : 0;
            BigDecimal currentYearVolume = seller.getCurrentYearGrossVolumePln() != null ? seller.getCurrentYearGrossVolumePln() : BigDecimal.ZERO;

            seller.setCurrentYearSalesCount(currentYearCount + 1);
            seller.setCurrentYearGrossVolumePln(currentYearVolume.add(itemPrice));


            // STEP 4: Flush everything to your Railway instance in a single batch query!
            sellerProfileRepo.save(seller);

        } catch (InvalidOperationException e) {
            log.error("Validation Violation: Failed to match seller entities during accounting updates", e);
            throw e;
        } catch (Exception e) {
            log.error("Financial System Exception: Critical failure updating transaction rows", e);
            throw new InvalidOperationException("Could not update seller profiles due to an internal server error.");
        }
    }


    /**
     *  ACCOUNTING-GRADE REFUND ADJUSTMENT ENGINE
     * Processes transaction reversals safely by preserving historical gross numbers
     * and recording the return values within a transparent settlement deduction layer.
     */
    @Transactional
    public void updateSellerTotalPayoutWithRefund(ListingOrderItem orderItem) {
        if (orderItem == null || orderItem.getSeller() == null) {
            log.error("Refund Engine: Aborting execution. Missing Order Item or Seller payload context.");
            return;
        }

        Long sellerUserId = orderItem.getSeller().getId();

        sellerProfileRepo.findById(sellerUserId).ifPresentOrElse(seller -> {
            log.warn("Refund Engine: Processing financial return transaction for Seller: {} [Order Item ID: {}]",
                    seller.getUser().getUsername(), orderItem.getId());

            // Protect arithmetic against uninstantiated null fee fields safely
            BigDecimal itemPrice = orderItem.getSellerPrice() != null ? orderItem.getSellerPrice() : BigDecimal.ZERO;

            //  STEP 1: PRESERVE HISTORICAL GROSS VALUES (Do NOT touch totalSales!)
            // Instead, we subtract the return volume directly out of their net payout settlement account ledger.
            BigDecimal currentSettlement = seller.getTotalSettlement() != null ? seller.getTotalSettlement() : BigDecimal.ZERO;
            seller.setTotalSettlement(currentSettlement.subtract(itemPrice));


            // STEP 2: REGULATORY CALENDAR-YEAR TAX ADJUSTMENTS
            // Note: DAC7 regulations mandate that gross volume calculations reflect actual value processing.
            // If the transaction failed before fulfillment, update the current year gross volume accordingly.
            BigDecimal currentYearVolume = seller.getCurrentYearGrossVolumePln() != null ? seller.getCurrentYearGrossVolumePln() : BigDecimal.ZERO;
            if (currentYearVolume.compareTo(itemPrice) >= 0) {
                seller.setCurrentYearGrossVolumePln(currentYearVolume.subtract(itemPrice));
            } else {
                seller.setCurrentYearGrossVolumePln(BigDecimal.ZERO);
            }

            //update sales count
            int currentSalesCount = seller.getCurrentYearSalesCount() != null ? seller.getCurrentYearSalesCount() : 0;
            if (currentSalesCount >= 0) {
                seller.setCurrentYearSalesCount(currentSalesCount-1);
            } else {
                seller.setCurrentYearSalesCount(0);
            }

            //update cancelled orders count;
            long currentCancelledOrders = seller.getNumberOfCancelledOrders()!=null?seller.getNumberOfCancelledOrders():0L;
            seller.setNumberOfCancelledOrders(currentCancelledOrders+1);
            // Save our clean database updates back to your Railway instance rows
            sellerProfileRepo.save(seller);

            log.info("Refund Engine: Reversal successfully logged. Total Gross Sales preserved. Net Settlement adjusted down by -{} zł",
                   itemPrice);

        }, () -> {
            log.error("Refund Engine: Critical data desync. Seller Profile missing for User ID: {}", sellerUserId);
            throw new InvalidOperationException("Could not execute refund: Vendor profile reference structure missing.");
        });
    }

    public void updateSellerTotalPayoutWithOrderRefund(ListingOrder order){
        if(order.getItems().isEmpty()){
            return;
        }
        for(ListingOrderItem item : order.getItems()){
            updateSellerTotalPayoutWithRefund(item);
        }
    }


    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public BigDecimal outStandingPayout(Jwt jwt){
        if(jwt!=null){
            AppUser appUser = appUserDetailsService.getAppUserByUsername(jwt.getSubject());

            SellerProfile sellerProfile = sellerProfileRepo.findByUser(appUser).orElse(null);

            if(sellerProfile!=null){
                BigDecimal settlements = sellerProfile.getTotalSettlement()!=null?sellerProfile.getTotalSettlement():BigDecimal.ZERO;
              BigDecimal sales = sellerProfile.getTotalSales()!=null?sellerProfile.getTotalSales():BigDecimal.ZERO;

               return sales.add(settlements);
            }
            return BigDecimal.ZERO;
        }
        return BigDecimal.ZERO;
    }
}
