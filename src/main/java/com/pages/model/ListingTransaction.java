package com.pages.model;

import com.pages.dto.InventoryItemPriceResponse;
import com.pages.dto.ListTransRequest;
import com.pages.dto.ListTransResponse;
import com.pages.dto.MediaResponse;
import com.pages.enums.ListingStatus;
import com.pages.util.LogEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.List;

@Entity
@Getter
@Setter
@Builder
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class ListingTransaction extends LogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inventory_item_id", nullable = false)
    @ToString.Exclude
    private InventoryItem inventory;


    @Enumerated(EnumType.STRING)
    private ListingStatus listingStatus;

    public ListingTransaction(ListTransRequest dto) {
        this.inventory= dto.getInventory();
        this.listingStatus = dto.getListingStatus();
    }

    public static ListTransResponse toListTransResponse(ListingTransaction listing, List<MediaResponse> media,InventoryItemPriceResponse priceDto) {

        return ListTransResponse.builder()
                .listingId(listing.getId())
                .brand(listing.getInventory().getProductCatalog().getBrand().getName())
                .productId(listing.getInventory().getProductCatalog().getId())
                .productName(listing.getInventory().getProductCatalog().getName())
                .productDescription(listing.getInventory().getProductCatalog().getDescription())
                .category(listing.getInventory().getProductCatalog().getCategory().getSlug())
                .productSlug(listing.getInventory().getProductCatalog().getSlug())
                .inventoryStatus(listing.getInventory().getStatus())
                .inventoryId(listing.getInventory().getId())
                .sku(listing.getInventory().getSku())
                .shipping(listing.getInventory().getDeliveryInfo())
                .shippingMethod(listing.getInventory().getShippingMethod().name())
                .createdAt(listing.getCreatedAt())
                .productCondition(listing.getInventory().getProductCondition().getName())
                .listingStatus(listing.getListingStatus())
                .priceDto(priceDto)
                .sellerId(listing.getInventory().getSeller().getId())
                .media(media)
                .build();
    }

    public static ListTransResponse toListTransResponse(ListingTransaction listing, MediaResponse media, InventoryItemPriceResponse priceDto) {

        return ListTransResponse.builder()
                .listingId(listing.getId())
                .brand(listing.getInventory().getProductCatalog().getBrand().getName())
                .productId(listing.getInventory().getProductCatalog().getId())
                .productCondition(listing.getInventory().getProductCondition().getName())
                .productName(listing.getInventory().getProductCatalog().getName())
                .productDescription(listing.getInventory().getProductCatalog().getDescription())
                .category(listing.getInventory().getProductCatalog().getCategory().getSlug())
                .productSlug(listing.getInventory().getProductCatalog().getSlug())
                .inventoryStatus(listing.getInventory().getStatus())
                .sku(listing.getInventory().getSku())
                .createdAt(listing.getCreatedAt())
                .shipping(listing.getInventory().getDeliveryInfo())
                .shippingMethod(listing.getInventory().getShippingMethod().name())
                .inventoryId(listing.getInventory().getId())
                .listingStatus(listing.getListingStatus())
                .sellerId(listing.getInventory().getSeller().getId())
                .priceDto(priceDto)
                .media(List.of(media))
                .build();
    }
}
