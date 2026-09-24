package com.pages.model;

import com.pages.dto.SellerProfileDto;
import com.pages.enums.SellerStatus;
import com.pages.util.LogEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

@Entity
@Table(name = "seller_profile")
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Setter
@ToString
public class SellerProfile extends LogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private AppUser user;

    @Enumerated(EnumType.STRING)
    private SellerStatus status;

    private boolean identityVerified;

    private Instant verifiedAt;

    @Column(precision = 10, scale = 2)
    private BigDecimal totalSales = BigDecimal.ZERO;
    @Column( precision = 10, scale = 2)
    private BigDecimal totalSettlement = BigDecimal.ZERO;

    private Integer successfulOrders;

    private Integer cancelledOrders;


    public SellerProfile(SellerProfileDto dto){
        this.cancelledOrders=dto.getCancelledOrders();
        this.status = dto.getStatus();
        this.totalSales = dto.getTotalSales();
        this.successfulOrders = dto.getSuccessfulOrders();
        this.user = dto.getUser();
    }



}
