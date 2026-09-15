package com.pages.dto;

import com.pages.model.InventoryItem;
import com.pages.model.ListingOrder;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ListingOrderItemDto {

    private Long id;
    private ListingOrder listingOrder;

    private Long sellerId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal finalizedPrice;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal shippingCost;
    private Long listingId;

    private InventoryItem inventoryItem;
}
