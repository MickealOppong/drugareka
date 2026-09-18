package com.pages.dto;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class EmailProductItemDto {
    private String productName;
   private BigDecimal amount;
   private Long quantity=1L;
   private BigDecimal shippingCost;
}
