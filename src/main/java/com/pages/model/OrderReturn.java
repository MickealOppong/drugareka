package com.pages.model;

import com.pages.enums.ReturnReason;
import com.pages.util.LogEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "order_returns")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderReturn extends LogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private ListingOrderItem listingOrderItem;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_code", nullable = false)
    private ReturnReason returnReason;

    // Optional text box parameter mapping for details or admin review footnotes
    @Column(name = "buyer_comment", length = 1000)
    private String buyerComment;

    @Column(name = "admin_notes", length = 1000)
    private String adminNotes;

    @Column(nullable = false)
    private String status;

    private Instant resolvedAt;
    private boolean sellerDebited;
    //  Calculates only this specific item's financial values
    @Column(name = "seller_deduction_amount", precision = 12, scale = 2)
    private BigDecimal sellerDeductionAmount;
}
