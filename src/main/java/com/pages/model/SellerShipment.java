package com.pages.model;

import com.pages.enums.ShipmentStatus;
import com.pages.util.LogEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "seller_shipment") // Explicit naming prevents naming bugs in MySQL
@Setter
@Getter
@ToString(callSuper = true)
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SellerShipment extends LogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "listing_order_item_id", nullable = false)
    private ListingOrderItem listingOrderItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", nullable = false)
    private SellerProfile seller;

    @Enumerated(EnumType.STRING)
    @Column(name = "shipment_status", nullable = false,length = 200)
  private ShipmentStatus shipmentStatus;

    private String shippingAddress;

    private Instant shippedAt;

    private Instant deliveredAt;

    @Column(length = 1024)
    private String comment;

    private String trackingNumber;
    private String inPostShipmentId;

}
