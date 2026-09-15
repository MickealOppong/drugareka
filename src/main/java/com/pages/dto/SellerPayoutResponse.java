package com.pages.dto;

import com.pages.enums.PayoutStatus;
import com.pages.model.ListingOrderItem;
import com.pages.model.SellerProfile;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SellerPayoutResponse {
    private Long id;

    private String seller;

    private Long listingOrderItem;

    private String orderNumber;
    private BigDecimal amount;

    private String currency;

    @Enumerated(EnumType.STRING)
    private PayoutStatus status;

    private Instant paidAt;
}
