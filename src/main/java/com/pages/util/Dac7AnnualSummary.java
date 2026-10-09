package com.pages.util;

import com.pages.model.SellerProfile;
import jakarta.persistence.*;
import lombok.*;
import lombok.extern.java.Log;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
        name = "dac7_annual_summary",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_dac7_seller_tax_year", columnNames = {"seller_profile_id", "tax_year"})
        },
        indexes = {@Index(name = "idx_dac7_tax_year", columnList = "tax_year"),
                @Index(name = "idx_dac7_seller", columnList = "seller_profile_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Dac7AnnualSummary extends LogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "seller_profile_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_dac7_summary_seller")
    )
    private SellerProfile sellerProfile;

    /**
     * Calendar year this summary belongs to.
     * Example: 2026
     */
    @Column(name = "tax_year", nullable = false)
    private Integer taxYear;

    /**
     * Number of qualifying DAC7 sales during the calendar year.
     */
    @Column(name = "sales_count", nullable = false)
    private Integer salesCount;

    /**
     * Gross value of qualifying sales during the calendar year.
     */
    @Column(
            name = "gross_volume",
            nullable = false,
            precision = 19,
            scale = 2
    )
    private BigDecimal grossVolume;

}