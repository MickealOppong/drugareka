package com.pages.dto;

import jakarta.persistence.Column;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Set;

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
    private Set<Long> shipmentIds;
    private String name;
}
