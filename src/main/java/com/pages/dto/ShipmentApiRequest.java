package com.pages.dto;


import jakarta.persistence.Column;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ShipmentApiRequest {

    private Sender sender;

    private Receiver receiver;

    private DpdPackage shipment;

    private String orderReference;
    @Column(length = 600)
    private String comment;
}
