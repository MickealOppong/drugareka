package com.pages.model;

import com.pages.util.LogEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "service_charges")
public class ServiceCharge extends LogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The order that generated the charge.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private ListingOrder order;

    /**
     * Seller responsible for the charge.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "buyer_id", nullable = false)
    private AppUser buyer;


    /**
     * Amount before tax.
     */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal netAmount;

    /**
     * VAT amount.
     */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal vatAmount;

    /**
     * Total charge including VAT.
     */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal grossAmount;

    /**
     * Currency used for the charge.
     */
    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = "PLN";


    /**
     * Optional description shown in accounting records.
     */
    @Column(length = 500)
    private String description;
}
