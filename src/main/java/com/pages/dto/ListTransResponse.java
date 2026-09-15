package com.pages.dto;

import com.pages.enums.InventoryStatus;
import com.pages.enums.ListingStatus;
import com.pages.model.InventoryItemPrice;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ListTransResponse {

    private Long listingId;
    private Long inventoryId;

    private Long productId;

    private String brand;
    private Long sellerId;

    private String category;

    private String productName;

    private String productSlug;

    private String productDescription;


    private String productCondition;

    private InventoryItemPriceResponse priceDto;

    @Enumerated(EnumType.STRING)
    private InventoryStatus inventoryStatus;

    private String sku;
    @Enumerated(EnumType.STRING)
    private ListingStatus listingStatus;
    private List<MediaResponse> media;
    private Instant createdAt;
    private String shippingMethod;
    private String shipping;
}
