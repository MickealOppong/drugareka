package com.pages.model;

import com.pages.enums.ShipmentStatus; // e.g., CREATED, IN_TRANSIT, DELIVERED
import com.pages.util.LogEntity;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "return_shipments")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReturnShipment extends LogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_return_id", nullable = false, unique = true)
    private OrderReturn orderReturn;

    private String address;

    private String trackingNumber;
    private String shipmentId;
    private String labelUrl;
    @Column(length = 1024)
    private String comment;
    private String itemSize;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ShipmentStatus shipmentStatus;
    private Instant deliveredAt;
    private Instant shippedAt;
    private String carrierShipmentId;
    private String carrier;
    @Column(name = "receipt_confirmation_token", unique = true)
    private String receiptConfirmationToken;

    @Column(name = "receipt_confirmation_token_expires_at")
    private Instant receiptConfirmationTokenExpiresAt;

    @Column(name = "receipt_confirmed_at")
    private Instant receiptConfirmedAt;
}
