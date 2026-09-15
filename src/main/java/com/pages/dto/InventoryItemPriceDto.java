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
public class InventoryItemPriceDto {

    private Long id;

    private BigDecimal sellerOldPrice;
    private BigDecimal sellerNewPrice;
    private InventoryItem inventoryItem;

    private BigDecimal storeOldPrice;
    private BigDecimal storeNewPrice;

    private String reason;
}
