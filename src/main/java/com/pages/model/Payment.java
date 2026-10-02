package com.pages.model;

import com.pages.enums.PaymentProvider;
import com.pages.enums.PaymentStatus;
import com.pages.util.LogEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payment extends LogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private ListingOrder listingOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentProvider provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    /**
     * Provider-specific checkout/session identifier.
     *
     * Stripe:
     *     Checkout Session ID
     *
     * PayU:
     *     PayU order/session ID
     */
    @Column(name = "provider_session_id")
    private String providerSessionId;

    /**
     * Stripe PaymentIntent ID.
     */
    @Column(name = "stripe_payment_intent_id")
    private String stripePaymentIntentId;

    /**
     * Provider transaction/order identifier when needed.
     */
    @Column(name = "provider_transaction_id")
    private String providerTransactionId;

    private String receiptUrl;

    private Instant paidAt;
}