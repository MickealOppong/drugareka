package com.pages.model;

import com.pages.dto.SellerProfileDto;
import com.pages.enums.SellerStatus;
import com.pages.util.LogEntity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "seller_profile", indexes = {
        @Index(name = "idx_seller_pesel", columnList = "pesel"),
        @Index(name = "idx_seller_dac7", columnList = "isDac7Reportable")
})
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Setter
@ToString
public class SellerProfile extends LogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private AppUser user;

    @Enumerated(EnumType.STRING)
    private SellerStatus status;

    private boolean identityVerified;

    private Instant verifiedAt;

    @Column(precision = 10, scale = 2)
    private BigDecimal totalSales = BigDecimal.ZERO;

    @Column(precision = 10, scale = 2)
    private BigDecimal totalSettlement = BigDecimal.ZERO;


    // MANDATORY PRIVATE ID COMPLIANCE FIELDS ---
    @Column(length = 11)
    private String pesel; // Validated via your 11-digit regex function block

    private String fullLegalName;

    private String permanentResidentialAddress;

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




    public SellerProfile(SellerProfileDto dto) {
        if (dto == null) return;

        // --- Core Base Relationship Links ---
        this.user = dto.getUser();
        this.status = dto.getStatus();
        this.identityVerified = dto.isIdentityVerified();
        this.verifiedAt = dto.getVerifiedAt();

        // --- Lifetime Financial and Order Accumulators ---
        this.totalSales = dto.getTotalSales() != null ? dto.getTotalSales() : BigDecimal.ZERO;
        this.totalSettlement = dto.getTotalSettlement() != null ? dto.getTotalSettlement() : BigDecimal.ZERO;

        // --- Mandatory Private Citizen Regulatory Verification Fields ---
        this.pesel = dto.getPesel(); // Governed by your 11-digit regex security mask
        this.fullLegalName = dto.getFullLegalName();
        this.permanentResidentialAddress = dto.getPermanentResidentialAddress();
        this.dateOfBirth = dto.getDateOfBirth();

        // --- Regulatory Calendar-Year DAC7 Tax Counters ---
        this.currentYearSalesCount = dto.getCurrentYearSalesCount() != null ? dto.getCurrentYearSalesCount() : 0;
        this.currentYearGrossVolumePln = dto.getCurrentYearGrossVolumePln() != null ? dto.getCurrentYearGrossVolumePln() : BigDecimal.ZERO;
        this.isDac7Reportable = dto.isDac7Reportable();
        this.dac7FlaggedAt = dto.getDac7FlaggedAt();
    }
}
