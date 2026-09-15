package com.pages.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AnalyticsDto {

    private Long ordersCount;
    private Long wishlistCount;
    private Long listingCount;
    private Long itemSoldCount;
    private Long awaitingShipmentCount;
    private BigDecimal outstandingPayout;
}
