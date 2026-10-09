package com.pages.dto;

import com.pages.enums.SellerStatus;
import com.pages.model.AppUser;
import com.pages.model.SellerProfile;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Data
@Builder
public class SellerProfileDto {

    private AppUser user;

    private SellerStatus status;

    private boolean identityVerified;

    private Instant verifiedAt;

    private BigDecimal totalSales = BigDecimal.ZERO;

    private BigDecimal totalSettlement = BigDecimal.ZERO;


    private String pesel;

    private String fullLegalName;

    private String permanentResidentialAddress;

    private LocalDate dateOfBirth;

    private Integer currentYearSalesCount = 0;

    private BigDecimal currentYearGrossVolumePln = BigDecimal.ZERO;

    private boolean isDac7Reportable = false;

    private Instant dac7FlaggedAt;
}
