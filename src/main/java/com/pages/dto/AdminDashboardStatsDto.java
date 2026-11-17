package com.pages.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class AdminDashboardStatsDto {

    private StatDto users;
    private StatDto products;
    private StatDto categories;
    private StatDto brands;
    private StatDto listings;
    private StatDto Sales;
    private StatDto orders;
    private StatDto sellers;

    @Data
    @Builder
    public static class StatDto {
        private long total;
        private long previousTotal;
        private long increase;
        private BigDecimal percentageChange;
    }
}
