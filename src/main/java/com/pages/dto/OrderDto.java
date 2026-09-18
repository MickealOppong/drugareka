package com.pages.dto;

import com.pages.enums.OrderStatus;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
public class OrderDto {

    private Long id;
    private String buyer;
    @Enumerated(EnumType.STRING)
    private OrderStatus orderStatus;
    private String currency;
    private Instant createdAt;
    private Instant paidAt;

    private String seller;
    private String orderNumber;
    private BigDecimal shipping;
    private String deliveryStatus;
    private String trackingNumber;


    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal orderTotal;

}
