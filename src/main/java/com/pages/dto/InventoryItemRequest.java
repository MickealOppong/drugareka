package com.pages.dto;

import com.pages.enums.InventoryStatus;
import com.pages.model.ProductCatalog;
import com.pages.model.SellerProfile;
import lombok.*;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class InventoryItemRequest {

    private SellerProfile seller;

    private ProductCatalog productCatalog;

    private String condition;
    private String shippingInfo;
    private String sku;

    private InventoryStatus status;
    private String shippingMethod;
}
