package com.pages.model;

import com.pages.enums.OfferStatus;
import com.pages.util.LogEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;


import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "listing_offers")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ListingOffer extends LogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long listingId;

    @Column(nullable = false)
    private Long buyerId;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal offeredPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OfferStatus status;

    private Instant expiresAt;
}
