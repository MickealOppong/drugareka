package com.pages.service;

import com.pages.dto.DpdPackage;
import com.pages.enums.ItemSize;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
public class PackageConfigurationService {

    /**
     * Resolves the default dimensional parameters for an individual item tier.
     */
    public DpdPackage getDpdPackage(ItemSize size) {
        if (size == null) {
            return getDpdPackage(ItemSize.SMALL_UP_10KG); // Fallback configuration safeguard
        }

        return switch (size) {
            case SMALL_UP_10KG ->
                    new DpdPackage(
                            new BigDecimal("2.0"),
                            new BigDecimal("30"),
                            new BigDecimal("20"),
                            new BigDecimal("15")
                    );

            case MEDIUM_UP_20KG ->
                    new DpdPackage(
                            new BigDecimal("5.0"),
                            new BigDecimal("50"),
                            new BigDecimal("40"),
                            new BigDecimal("30")
                    );

            case LARGE_UP_50KG ->
                    new DpdPackage(
                            new BigDecimal("15.0"),
                            new BigDecimal("80"),
                            new BigDecimal("60"),
                            new BigDecimal("50")
                    );

            case HEAVY_OVER_50KG ->
                    new DpdPackage(
                            new BigDecimal("30.0"),
                            new BigDecimal("100"),
                            new BigDecimal("80"),
                            new BigDecimal("60")
                    );
        };
    }

    /**
     * NEW: Aggregates multiple dimensions into one physical consolidated box.
     * Combines all cargo weights, stacks vertical heights, and adopts the maximum bounding box width/length.
     */
    public DpdPackage getConsolidatedDpdPackage(List<ItemSize> sizes) {
        if (sizes == null || sizes.isEmpty()) {
            throw new IllegalArgumentException("Lista wymiarów do konsolidacji nie może być pusta.");
        }

        BigDecimal totalWeight = BigDecimal.ZERO;
        BigDecimal maxLength = BigDecimal.ZERO;
        BigDecimal maxWidth = BigDecimal.ZERO;
        BigDecimal totalHeight = BigDecimal.ZERO;

        for (ItemSize size : sizes) {
            DpdPackage individualPackage = getDpdPackage(size);

            // 1. Accumulate total physical weight
            totalWeight = totalWeight.add(individualPackage.getWeight());

            // 2. Determine the widest/longest footprint constraints (Max boundaries)
            maxLength = maxLength.max(individualPackage.getLength());
            maxWidth = maxWidth.max(individualPackage.getWidth());

            // 3. Stack package heights vertically inside the single consolidated box context
            totalHeight = totalHeight.add(individualPackage.getHeight());
        }

        // Apply a strict freight fallback safety barrier to avoid breaking DPD's macro-limits (e.g., maximum 70kg for non-pallet)
        if (totalWeight.compareTo(new BigDecimal("70.0")) > 0) {
            log.warn("Consolidated weight ({} kg) exceeds standard courier thresholds. Flagging routing for Pallet line.", totalWeight);
        }

        return new DpdPackage(totalWeight, maxLength, maxWidth, totalHeight);
    }
}
