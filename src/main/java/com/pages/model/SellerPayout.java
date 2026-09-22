package com.pages.model;
import com.pages.enums.PayoutStatus;
import com.pages.util.LogEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
@Entity
@Table(name = "seller_payout")
@Setter
@Getter
@ToString
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SellerPayout extends LogEntity{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    private SellerProfile seller;

    @ManyToOne(fetch = FetchType.LAZY)
    private ListingOrderItem listingOrderItem;

    @Column( nullable = false, precision = 10, scale = 2)
    private BigDecimal amount = BigDecimal.ZERO;

    private String currency;

    @Enumerated(EnumType.STRING)
    private PayoutStatus status;

    private Instant paidAt;
}
