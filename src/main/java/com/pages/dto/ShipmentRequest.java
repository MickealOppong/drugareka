package com.pages.dto;

import com.pages.enums.ShipmentStatus;
import com.pages.model.ListingOrder;
import com.pages.model.SellerProfile;
import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;
@Data
public class ShipmentRequest {

    private Long listingOrderId;
    private Long sellerId;

    @Enumerated(EnumType.STRING)
    private ShipmentStatus status;

    private Instant shippedAt;

    private Instant deliveredAt;
}
