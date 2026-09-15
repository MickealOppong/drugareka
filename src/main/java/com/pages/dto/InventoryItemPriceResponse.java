package com.pages.dto;

import com.pages.model.InventoryItem;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class InventoryItemPriceResponse {

    private Long id;

    private BigDecimal sellerOLdPrice;
    private BigDecimal sellerNewPrice;
    private Long inventoryId;

    private BigDecimal storeOldPrice;
    private BigDecimal storeNewPrice;

    private String reason;
}
