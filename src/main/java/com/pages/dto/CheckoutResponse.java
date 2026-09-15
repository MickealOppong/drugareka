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
public class CheckoutResponse {

    private Long orderId;

    private Long paymentId;

    private String paymentReference;

    private BigDecimal total;

    private String paymentUrl;
}
