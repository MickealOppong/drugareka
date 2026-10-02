package com.pages.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "seller_shipment_confirmation")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SellerShipmentToken {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 128, nullable = false, unique = true)
    private String token;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant usedAt;
}