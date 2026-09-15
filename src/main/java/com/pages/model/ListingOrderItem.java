package com.pages.model;

import com.pages.enums.ShippingMethod;
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
public class ListingOrderItem extends LogEntity {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne
    @JoinColumn(name = "listing_order_id",nullable = false)
    private ListingOrder listingOrder;

    @ManyToOne
    @JoinColumn(name = "seller_id",nullable = false)
    private SellerProfile seller;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal finalizedPrice;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal shippingCost;
    private Long listingId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "inventory_item_id",
            nullable = false
    )
    private InventoryItem inventoryItem;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ShippingMethod shippingMethod;
}
