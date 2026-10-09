package com.pages.service;

import com.pages.dto.ResponseDto;
import com.pages.dto.SellerProfileDto;
import com.pages.exception.EntityNotFoundException;
import com.pages.exception.InvalidOperationException;
import com.pages.model.*;
import com.pages.repository.SellerPayoutRepo;
import com.pages.repository.SellerProfileRepo;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Slf4j
@Service
public class SellerProfileService {

    // PLATFORM PARAMETER CONSTANTS: Legal DAC7 threshold boundaries for EU consumer markets
    private static final int DAC7_MAX_SALES_COUNT = 30;
    private static final BigDecimal DAC7_MAX_GROSS_VOLUME_PLN = new BigDecimal("8600.00"); // Approx €2,000 equivalent


    private final SellerProfileRepo sellerProfileRepo;
    private final AppUserDetailsService appUserDetailsService;
    private final SellerPayoutRepo sellerPayoutRepo;

    public SellerProfileService(SellerProfileRepo sellerProfileRepo, AppUserDetailsService appUserDetailsService, SellerPayoutRepo sellerPayoutRepo) {
        this.sellerProfileRepo = sellerProfileRepo;
        this.appUserDetailsService = appUserDetailsService;
        this.sellerPayoutRepo = sellerPayoutRepo;
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

    public SellerProfile getSellerProfile(Long userId){
        return sellerProfileRepo.findByUserId(userId).orElse(null);
    }


    /**
     *
     * Aggregates total order lines, resolves multiple seller balances simultaneously without loop thrashing,
     * updates lifetime metrics, and dynamically computes calendar-year DAC7 tax compliance thresholds.
     */
    @Transactional
    public List<SellerProfile> updateSellerTotalSales(List<ListingOrderItem> orderItems) {
        if (orderItems == null || orderItems.isEmpty()) {
            return new ArrayList<>();
        }

        try {
            // STEP 1: Aggregate order financial splits by Seller ID to prevent multiple database hits
            Map<Long, BigDecimal> sellerOrderTotals = new HashMap<>();

            for (ListingOrderItem item : orderItems) {
                if (item.getSeller() == null || item.getSeller().getId() == null) {
                    continue;
                }

                Long sellerId = item.getSeller().getId();

                // Protect arithmetic against uninstantiated null fee fields safely
                BigDecimal itemPrice = item.getSellerPrice() != null ? item.getSellerPrice() : BigDecimal.ZERO;
                BigDecimal shippingCost = item.getShippingCost() != null ? item.getShippingCost() : BigDecimal.ZERO;
                BigDecimal totalLineAmount = itemPrice.add(shippingCost);

                sellerOrderTotals.put(sellerId, sellerOrderTotals.getOrDefault(sellerId, BigDecimal.ZERO).add(totalLineAmount));
            }

            List<SellerProfile> profilesToUpdate = new ArrayList<>();

            // STEP 2: Process each distinct vendor exactly once
            for (Map.Entry<Long, BigDecimal> entry : sellerOrderTotals.entrySet()) {
                Long sellerId = entry.getKey();
                BigDecimal aggregatedOrderTotal = entry.getValue();

                SellerProfile seller = sellerProfileRepo.findById(sellerId)
                        .orElseThrow(() -> new InvalidOperationException("Seller profile not found for user ID: " + sellerId));

                log.info("Financial System: Aggregating order funds for seller profile: {} [Amount: +{} zł]",
                        seller.getUser().getUsername(), aggregatedOrderTotal);

                // --- Update Lifetime Accounting Metrics ---
                BigDecimal currentLifetimeSales = seller.getTotalSales() != null ? seller.getTotalSales() : BigDecimal.ZERO;
                seller.setTotalSales(currentLifetimeSales.add(aggregatedOrderTotal));

                // --- Update Calendar-Year Regulatory Counters ---
                int currentYearCount = seller.getCurrentYearSalesCount() != null ? seller.getCurrentYearSalesCount() : 0;
                BigDecimal currentYearVolume = seller.getCurrentYearGrossVolumePln() != null ? seller.getCurrentYearGrossVolumePln() : BigDecimal.ZERO;

                seller.setCurrentYearSalesCount(currentYearCount + 1);
                seller.setCurrentYearGrossVolumePln(currentYearVolume.add(aggregatedOrderTotal));

                // STEP 3: DYNAMIC LEGAL RISK GUARD
                // Automatically flags private citizen merchants if they cross EU/Polish tracking limits
                if (!seller.isDac7Reportable()) {
                    boolean crossedSalesCount = seller.getCurrentYearSalesCount() >= DAC7_MAX_SALES_COUNT;
                    boolean crossedVolumeLimit = seller.getCurrentYearGrossVolumePln().compareTo(DAC7_MAX_GROSS_VOLUME_PLN) >= 0;

                    if (crossedSalesCount || crossedVolumeLimit) {
                        log.warn("Regulatory Guard Shield: Private individual merchant [{}] has crossed legal tracking limits! Flagging profile for DAC7 reporting audits.",
                                seller.getFullLegalName());
                        seller.setDac7Reportable(true);
                        seller.setDac7FlaggedAt(Instant.now());
                    }
                }

                profilesToUpdate.add(seller);
            }

            // STEP 4: Flush everything to your Railway instance in a single batch query!
            return sellerProfileRepo.saveAll(profilesToUpdate);

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

        sellerProfileRepo.findByUserId(sellerUserId).ifPresentOrElse(seller -> {
            log.warn("Refund Engine: Processing financial return transaction for Seller: {} [Order Item ID: {}]",
                    seller.getUser().getUsername(), orderItem.getId());

            // Protect arithmetic against uninstantiated null fee fields safely
            BigDecimal itemPrice = orderItem.getSellerPrice() != null ? orderItem.getSellerPrice() : BigDecimal.ZERO;
            BigDecimal shippingCost = orderItem.getShippingCost() != null ? orderItem.getShippingCost() : BigDecimal.ZERO;
            BigDecimal totalRefundVolume = itemPrice.add(shippingCost);

            //  STEP 1: PRESERVE HISTORICAL GROSS VALUES (Do NOT touch totalSales!)
            // Instead, we subtract the return volume directly out of their net payout settlement account ledger.
            BigDecimal currentSettlement = seller.getTotalSettlement() != null ? seller.getTotalSettlement() : BigDecimal.ZERO;
            seller.setTotalSettlement(currentSettlement.subtract(totalRefundVolume));


            // STEP 2: REGULATORY CALENDAR-YEAR TAX ADJUSTMENTS
            // Note: DAC7 regulations mandate that gross volume calculations reflect actual value processing.
            // If the transaction failed before fulfillment, update the current year gross volume accordingly.
            BigDecimal currentYearVolume = seller.getCurrentYearGrossVolumePln() != null ? seller.getCurrentYearGrossVolumePln() : BigDecimal.ZERO;
            if (currentYearVolume.compareTo(totalRefundVolume) >= 0) {
                seller.setCurrentYearGrossVolumePln(currentYearVolume.subtract(totalRefundVolume));
            } else {
                seller.setCurrentYearGrossVolumePln(BigDecimal.ZERO);
            }

            // Save our clean database updates back to your Railway instance rows
            sellerProfileRepo.save(seller);

            log.info("Refund Engine: Reversal successfully logged. Total Gross Sales preserved. Net Settlement adjusted down by -{} zł",
                    totalRefundVolume);

        }, () -> {
            log.error("Refund Engine: Critical data desync. Seller Profile missing for User ID: {}", sellerUserId);
            throw new InvalidOperationException("Could not execute refund: Vendor profile reference structure missing.");
        });
    }


    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public BigDecimal outStandingPayout(Jwt jwt){
        if(jwt!=null){
            AppUser appUser = appUserDetailsService.getAppUserByUsername(jwt.getSubject());

            SellerProfile sellerProfile = sellerProfileRepo.findByUser(appUser).orElse(null);

            if(sellerProfile!=null){
                BigDecimal settlements = sellerProfile.getTotalSettlement()!=null?sellerProfile.getTotalSettlement():BigDecimal.ZERO;
              BigDecimal sales = sellerProfile.getTotalSales()!=null?sellerProfile.getTotalSales():BigDecimal.ZERO;

               return sales.subtract(settlements);
            }
            return BigDecimal.ZERO;
        }
        return BigDecimal.ZERO;
    }
}
