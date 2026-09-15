package com.pages.model;


import com.pages.enums.OrderStatus;
import com.pages.util.LogEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ListingOrder extends LogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long buyerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus orderStatus;

    private String shippingAddress;

    private String orderNumber;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subTotal;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal orderTotal;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalShipmentCost;

    private String currency;
    private Instant paidAt;

    @OneToMany(mappedBy = "listingOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ListingOrderItem> items = new ArrayList<>();



    public void addItem(ListingOrderItem item) {
        if (item != null) {
            // 1. Add the item to the local list collection
            if (this.items == null) {
                this.items = new ArrayList<>();
            }
            this.items.add(item);

            // 2. CRITICAL JOCKEY: Set the parent reference on the child item
            item.setListingOrder(this);
        }
    }

    public void removeItem(ListingOrderItem item) {
        if (item != null && this.items != null) {
            this.items.remove(item);
            item.setListingOrder(null); // Clears the reference so orphanRemoval can delete it
        }
    }
}