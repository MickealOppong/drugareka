package com.pages.dto;

import com.pages.enums.ReturnReason;
import com.pages.model.ListingOrderItem;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class OrderReturnRequest {

    private Long orderItemId;

    private String returnReason;

    @Column(name = "buyer_comment", length = 1000)
    private String buyerComment;

    @Column(nullable = false)
    private String status;
}
