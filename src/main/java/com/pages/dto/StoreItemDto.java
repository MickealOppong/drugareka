package com.pages.dto;


import com.pages.enums.InventoryStatus;
import com.pages.model.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class StoreItemDto {
    private Long inventoryId;

    private Long productId;

    private Brand brand;
    private Long sellerId;

    private Category category;

    private String productName;

    private String productSlug;

    private String productDescription;

    private ProductCondition productCondition;

    private List<InventoryItemPrice> inventoryItemPrice;

    private String inventoryStatus;
    private String listingStatus;
}
