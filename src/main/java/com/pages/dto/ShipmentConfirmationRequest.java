package com.pages.dto;

import jakarta.persistence.Column;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ShipmentConfirmationRequest {

    // Optional field
    @Column(length = 1024)
    private String comment;
    private String token;
    private AddressForm pickupAddress;
    private Long[] shipmentIds;
    private String name;
}
