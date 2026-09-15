package com.pages.model;

import com.pages.enums.CartStatus;
import com.pages.enums.ShippingMethod;
import com.pages.util.LogEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "cart_item")
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CartItem extends LogEntity{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long inventoryItemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cart_id", nullable = false)
    private Cart cart;

    private Long listingId;


    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    private Instant reservedUntil;

    @Enumerated(EnumType.STRING)
    private ShippingMethod shippingMethod;

}
