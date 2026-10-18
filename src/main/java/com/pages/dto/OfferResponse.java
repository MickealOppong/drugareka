package com.pages.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class OfferResponse {
    private BigDecimal price;
    private BigDecimal shipping;
    private BigDecimal serviceCharge;
}
