package com.pages.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.pages.enums.InventoryStatus;
import com.pages.enums.ItemSize;
import com.pages.enums.ShippingMethod;
import com.pages.util.LogEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "inventory_items") // Standardized to clean plural naming guidelines
@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryItem extends LogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnore
    @JoinColumn(name = "seller_id", nullable = false)
    @ToString.Exclude
    private SellerProfile seller;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_catalog_id", nullable = false)
    @ToString.Exclude
    private ProductCatalog productCatalog;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_condition_id", nullable = false)
    @ToString.Exclude
    private ProductCondition productCondition;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InventoryStatus status;

    private Long reservedBy;

    private Instant reservedUntil;

    private String deliveryInfo;

    @Enumerated(EnumType.STRING)
    @Column(name = "size_tier", nullable = false,length = 200)
    private ItemSize itemSize = ItemSize.SMALL_UP_10KG;

    private String sku;


}
