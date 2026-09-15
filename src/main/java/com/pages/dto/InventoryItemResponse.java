package com.pages.dto;

import com.pages.enums.InventoryStatus;
import com.pages.model.*;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class InventoryItemResponse {
    private Long id;
    private Long seller;

    private ProductCatalog productCatalog;

    private ProductCondition condition;

    private InventoryStatus status;
}
