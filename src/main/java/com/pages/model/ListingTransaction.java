package com.pages.model;

import com.pages.dto.*;
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

        CategoryResponse categoryResponse = CategoryResponse.builder()
                .name(listing.getInventory().getProductCatalog().getCategory().getName())
                .path(listing.getInventory().getProductCatalog().getCategory().getPath())
                .build();
        return ListTransResponse.builder()
                .listingId(listing.getId())
                .brand(listing.getInventory().getProductCatalog().getBrand().getName())
                .productId(listing.getInventory().getProductCatalog().getId())
                .productName(listing.getInventory().getProductCatalog().getName())
                .productDescription(listing.getInventory().getProductCatalog().getDescription())
                .category(categoryResponse)
                .productSlug(listing.getInventory().getProductCatalog().getSlug())
                .inventoryStatus(listing.getInventory().getStatus())
                .inventoryId(listing.getInventory().getId())
                .sku(listing.getInventory().getSku())
                .shipping(listing.getInventory().getDeliveryInfo())
                .createdAt(listing.getCreatedAt())
                .productCondition(listing.getInventory().getProductCondition().getName())
                .listingStatus(listing.getListingStatus())
                .itemSize(listing.getInventory().getItemSize().name())
                .priceDto(priceDto)
                .sellerId(listing.getInventory().getSeller().getUser().getId())
                .media(media)
                .build();
    }

    public static ListTransResponse toListTransResponse(ListingTransaction listing, MediaResponse media, InventoryItemPriceResponse priceDto) {

        CategoryResponse categoryResponse = CategoryResponse.builder()
                .name(listing.getInventory().getProductCatalog().getCategory().getName())
                .path(listing.getInventory().getProductCatalog().getCategory().getPath())
                .build();

        return ListTransResponse.builder()
                .listingId(listing.getId())
                .brand(listing.getInventory().getProductCatalog().getBrand().getName())
                .productId(listing.getInventory().getProductCatalog().getId())
                .productCondition(listing.getInventory().getProductCondition().getSlug())
                .productName(listing.getInventory().getProductCatalog().getName())
                .productDescription(listing.getInventory().getProductCatalog().getDescription())
                .category(categoryResponse)
                .productSlug(listing.getInventory().getProductCatalog().getSlug())
                .inventoryStatus(listing.getInventory().getStatus())
                .sku(listing.getInventory().getSku())
                .createdAt(listing.getCreatedAt())
                .shipping(listing.getInventory().getDeliveryInfo())
                .inventoryId(listing.getInventory().getId())
                .listingStatus(listing.getListingStatus())
                .sellerId(listing.getInventory().getSeller().getUser().getId())
                .priceDto(priceDto)
                .itemSize(listing.getInventory().getItemSize().name())
                .media(List.of(media))
                .build();
    }
}
