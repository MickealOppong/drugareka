package com.pages.model;


import com.pages.enums.PayoutExceptionReason;
import com.pages.enums.PayoutExceptionStatus;
import com.pages.util.LogEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(
        name = "payout_exception",
        indexes = {
                @Index(name = "idx_payout_exception_status", columnList = "status"),
                @Index(name = "idx_payout_exception_seller", columnList = "seller_id"),
                @Index(name = "idx_payout_exception_payout", columnList = "seller_payout_id")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayoutException extends LogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_payout_id", nullable = false)
    private SellerPayout sellerPayout;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_id", nullable = false)
    private SellerProfile seller;

    @Enumerated(EnumType.STRING)
    @Column(name = "exception_reason", nullable = false, length = 50)
    private PayoutExceptionReason exceptionReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private PayoutExceptionStatus status = PayoutExceptionStatus.PENDING_REVIEW;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "review_notes", length = 2000)
    private String reviewNotes;
}
