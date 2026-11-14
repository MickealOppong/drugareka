package com.pages.dto;

import com.pages.enums.SellerStatus;
import com.pages.model.AppUser;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Data
@Builder
public class SellerProfileResponse {

    private Long sellerId;
    private Long userId;

    private String status;

    private boolean identityVerified;

    private Instant verifiedAt;

    @Column(precision = 10, scale = 2)
    private BigDecimal totalSales = BigDecimal.ZERO;

    @Column(precision = 10, scale = 2)
    private BigDecimal totalSettlement = BigDecimal.ZERO;


    private String fullLegalName;


    private LocalDate dateOfBirth;

    //  REGULATORY CALENDAR-YEAR DAC7 TAX COUNTERS ---
    // Critical: DAC7 clears and resets back to 0 on January 1st every year,
    // which is why you cannot use your lifetime 'totalSales' or 'successfulOrders' for tax reporting!
    @Column(nullable = false)
    private Integer currentYearSalesCount = 0;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal currentYearGrossVolumePln = BigDecimal.ZERO;

    @Column(nullable = false)
    private boolean isDac7Reportable = false;

    private Instant dac7FlaggedAt;

    @Column(unique = true)
    private String stripeAccountId;

    @Column(nullable = false)
    private boolean stripeOnboardingComplete = false;

    @Column(nullable = false)
    private boolean stripeChargesEnabled = false;

    @Column(nullable = false)
    private boolean stripePayoutsEnabled = false;

    @Column(nullable = false)
    private boolean stripeTransfersEnabled = false;

    // Store Stripe's latest reported capability status, e.g. active,
// inactive, pending, or unrequested.
    private String stripeTransfersStatus;

    @Lob
    private String stripeCurrentlyDue;

    @Lob
    private String stripeEventuallyDue;

    @Lob
    private String stripePastDue;

    @Lob
    private String stripeVerificationErrors;

    private String stripeDisabledReason;

    private Instant stripeAccountUpdatedAt;

    private Long numberOfCancelledOrders;
}
