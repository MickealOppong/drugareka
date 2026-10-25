package com.pages.model;

import com.pages.enums.DisputeStatus;
import com.pages.enums.ReturnReason;
import com.pages.util.LogEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "order_disputes")
@Getter
@Setter
@ToString(exclude = "listingOrderItem")
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class OrderDispute extends LogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "ticket_id", nullable = false, unique = true)
    private String ticketId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(name = "order_number", nullable = false)
    private String orderNumber;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_item_id", nullable = false, unique = true)
    private ListingOrderItem listingOrderItem;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_code", nullable = false)
    private ReturnReason returnReason;

    @Column(name = "buyer_comment", length = 1000)
    private String buyerComment;

    @Column(name = "seller_comment", length = 1000)
    private String sellerComment;

    @Column(name = "admin_notes", length = 1000)
    private String adminNotes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_code", nullable = false)
    private DisputeStatus disputeStatus;

    @Column(name = "resolved_at")
    private Instant resolvedAt;
}
