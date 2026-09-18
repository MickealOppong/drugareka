package com.pages.repository;

import com.pages.enums.InventoryStatus;
import com.pages.enums.ListingStatus;
import com.pages.model.ListingTransaction;
import jdk.jfr.Registered;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

@Registered
public interface ListingTransactionRepo extends JpaRepository<ListingTransaction,Long>,JpaSpecificationExecutor<ListingTransaction> {

    Page<ListingTransaction> findByInventorySellerUserId(Long sellerUserId,Pageable pageable);
    List<ListingTransaction> findByListingStatus(ListingStatus listingStatus);
    Optional<ListingTransaction> findByIdAndInventoryStatus(Long id, InventoryStatus inventoryStatus);
    Optional<ListingTransaction> findByIdAndListingStatus(Long id, ListingStatus listingStatus);

    List<ListingTransaction> findByListingStatusAndInventoryProductCatalogCategoryNameAndInventorySellerIdNot(
            ListingStatus listingStatus,
            String category,
            Long appUserId
    );
    @Query("SELECT lt FROM ListingTransaction lt " +
            "WHERE lt.listingStatus = :listingStatus " +
            "AND (:category IS NULL OR lt.inventory.productCatalog.category.name = :category) " +
            "AND (:appUserId IS NULL OR lt.inventory.seller.id <> :appUserId)")
    Page<ListingTransaction> findShopFeed(
            @Param("listingStatus") ListingStatus listingStatus,
            @Param("category") String category,
            @Param("appUserId") Long appUserId ,
           Pageable pageable
    );


    List<ListingTransaction> findTop8ByInventoryStatusAndListingStatusOrderByCreatedAtDesc(InventoryStatus inventoryStatus,ListingStatus listingStatus);

    List<ListingTransaction> findTop10ByOrderByCreatedAtDesc();
    Optional<ListingTransaction> findFirstByInventorySellerIdAndListingStatusOrderByCreatedAtDesc(Long sellerId,ListingStatus listingStatus);
    Long countByInventorySellerIdAndInventoryStatusAndListingStatus(Long sellerId,InventoryStatus inventoryStatus,ListingStatus listingStatus);
    Optional<ListingTransaction> findByInventoryId(Long inventoryId);
}
