package com.pages.dto;

import com.pages.enums.CartStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CartItemDto {


    private Long listingId;
    private BigDecimal price;
    private Instant reservedUntil;
    private Long inventoryId;
    private Long cartItemId;
    private String productName;
    private String image;
    private BigDecimal shipping;
    private String shippingMethod;
}
