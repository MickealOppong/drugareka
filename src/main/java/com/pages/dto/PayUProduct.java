package com.pages.dto;


import com.pages.enums.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PayUProduct {
    private BigDecimal unitPrice;
    private String name;
  private Long quantity;
}
