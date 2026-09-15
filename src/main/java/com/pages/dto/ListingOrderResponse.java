package com.pages.dto;

import com.pages.enums.CartStatus;
import com.pages.enums.OrderStatus;
import com.pages.model.ListingOrder;
import com.pages.model.ListingOrderItem;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ListingOrderResponse {


    private Long id;
    private Long buyerId;
    @Enumerated(EnumType.STRING)
    private OrderStatus orderStatus;

    private String shippingAddress;
    private String orderNumber;
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subTotal;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal orderTotal;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalShipmentCost;

    private Instant paidAt;
    List<ListingOrderItemDto> orderItemDto;
}