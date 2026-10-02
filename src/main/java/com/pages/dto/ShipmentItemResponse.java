package com.pages.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ShipmentItemResponse {
    private Long shipmentId;
    private String orderNumber;
    private String productName;
}
