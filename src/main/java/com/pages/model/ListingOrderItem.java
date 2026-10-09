package com.pages.model;

import com.pages.enums.OrderItemStatus;
import com.pages.enums.ShippingMethod;
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

    @Column(name = "seller_name_snapshot", nullable = false)
    private String sellerNameSnapshot;

    @Column(name = "product_name_snapshot", nullable = false)
    private String productNameSnapshot;

    @Column(name = "product_condition_snapshot", nullable = false)
    private String productConditionSnapshot;

    @Column(name = "product_brand_snapshot", nullable = false)
    private String productBrandSnapshot;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal serviceCharge;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal sellerPrice;


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
    @Column(nullable = false,length = 50)
    private ShippingMethod shippingMethod;


    @Enumerated(EnumType.STRING)
    private OrderItemStatus orderItemStatus;

    @Column(name = "receipt_confirmation_token", unique = true)
    private String receiptConfirmationToken;

    @Column(name = "receipt_confirmation_token_expires_at")
    private Instant receiptConfirmationTokenExpiresAt;

    @Column(name = "receipt_confirmed_at")
    private Instant receiptConfirmedAt;

}
