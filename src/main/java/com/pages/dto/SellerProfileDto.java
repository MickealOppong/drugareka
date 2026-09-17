package com.pages.dto;

import com.pages.enums.SellerStatus;
import com.pages.model.AppUser;
import com.pages.model.SellerProfile;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
public class SellerProfileDto {

    private AppUser user;

    private SellerStatus status;

    private boolean identityVerified;

    private Instant verifiedAt;

    private BigDecimal totalSales =BigDecimal.ZERO;

    private Integer successfulOrders;

    private Integer cancelledOrders;
}
