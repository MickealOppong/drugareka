package com.pages.dto;

import com.pages.enums.ShipmentStatus;
import com.pages.model.ListingOrder;
import com.pages.model.SellerProfile;
import jakarta.persistence.*;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.Instant;
import java.time.LocalDate;

@Data
public class ShipmentRequest {

    private Long shipmentId;
    private String comment;
    private String status;
    private String trackingNumber;
    private LocalDate createdAt;
}
