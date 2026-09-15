package com.pages.dto;

import com.pages.enums.InventoryStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
public class WishListResponse {

    private Long id;
    private String image;
    private String inventoryStatus;
    private String productName;
    private Long listingId;
    private BigDecimal sellerPrice;
    private Instant createAt;

}
