package com.pages.dto;

import com.pages.enums.ShipmentStatus;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ShipmentResponse {
    private Long id;
    private String orderNumber;
    private Long listingOrderId;
    private String seller;
    private String deliveryAddress;
    private String trackingNumber;

    @Enumerated(EnumType.STRING)
    private ShipmentStatus status;

    private Instant shippedAt;

    private Instant deliveredAt;
}
