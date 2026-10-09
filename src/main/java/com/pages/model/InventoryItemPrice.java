package com.pages.model;


import com.pages.dto.InventoryItemPriceDto;
import com.pages.dto.InventoryItemPriceResponse;
import com.pages.util.LogEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

@Entity
@Getter
@Setter
@ToString
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class InventoryItemPrice extends LogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal sellerOldPrice = BigDecimal.ZERO;
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal sellerNewPrice =BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal oldServiceCharge = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal newServiceCharge = BigDecimal.ZERO;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventory_item_id", nullable = false)
    private InventoryItem inventoryItem;


    private String reason;

    public InventoryItemPrice(InventoryItemPriceDto dto){
        this.sellerOldPrice =dto.getSellerOldPrice();
        this.sellerNewPrice = dto.getSellerNewPrice();
        this.inventoryItem = dto.getInventoryItem();
        this.oldServiceCharge = dto.getOldServiceCharge();
        this.newServiceCharge = dto.getNewServiceCharge();
        this.reason = dto.getReason();
    }

    public InventoryItemPriceResponse toPriceDto(){
       return InventoryItemPriceResponse.builder()
                .id(this.getId())
               .sellerOLdPrice(this.sellerOldPrice)
                .sellerNewPrice(this.getSellerNewPrice())
               .inventoryId(this.inventoryItem.getId())
                .oldServiceCharge(this.oldServiceCharge)
               .newServiceCharge(this.newServiceCharge)
                .reason(this.reason)
                .build();
    }
}
