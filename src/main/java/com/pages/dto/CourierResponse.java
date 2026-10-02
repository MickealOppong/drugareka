package com.pages.dto;

import com.pages.enums.ItemSize;
import com.pages.enums.ShippingMethod;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CourierResponse {

    private Long id;

    @Enumerated(EnumType.STRING)
    private ItemSize itemSize;

    @Enumerated(EnumType.STRING)
    private ShippingMethod shippingMethod;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;
    private String category;

    private boolean active;
}
