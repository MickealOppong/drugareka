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
    private BigDecimal storeOldPrice = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal storeNewPrice = BigDecimal.ZERO;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventory_item_id", nullable = false)
    private InventoryItem inventoryItem;


    private String reason;

    public InventoryItemPrice(InventoryItemPriceDto dto){
        this.sellerOldPrice =dto.getSellerOldPrice();
        this.sellerNewPrice = dto.getSellerNewPrice();
        this.inventoryItem = dto.getInventoryItem();
        this.storeOldPrice = dto.getStoreOldPrice();
        this.storeNewPrice = dto.getStoreNewPrice();
        this.reason = dto.getReason();
    }

    public InventoryItemPriceResponse toPriceDto(){
       return InventoryItemPriceResponse.builder()
                .id(this.getId())
               .sellerOLdPrice(this.sellerOldPrice)
                .sellerNewPrice(this.getSellerNewPrice())
               .inventoryId(this.inventoryItem.getId())
                .storeOldPrice(this.storeOldPrice)
               .storeNewPrice(this.storeNewPrice)
                .reason(this.reason)
                .build();
    }
}
