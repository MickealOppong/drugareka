package com.pages.repository;

import com.pages.enums.InventoryStatus;
import com.pages.enums.ListingStatus;
import com.pages.model.ListingTransaction;
import com.pages.model.SellerProfile;
import jdk.jfr.Registered;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Registered
public interface ListingTransactionRepo extends JpaRepository<ListingTransaction,Long>,JpaSpecificationExecutor<ListingTransaction> {

    Page<ListingTransaction> findByInventorySellerUserId(Long sellerUserId,Pageable pageable);
    List<ListingTransaction> findByListingStatus(ListingStatus listingStatus);
    Optional<ListingTransaction> findByIdAndInventoryStatus(Long id, InventoryStatus inventoryStatus);
    Optional<ListingTransaction> findByIdAndListingStatus(Long id, ListingStatus listingStatus);
    Optional<ListingTransaction> findByIdAndInventoryProductCatalogSlug(Long id,String slug);

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



    List<ListingTransaction>
    findAllByInventoryStatusAndListingStatusAndCreatedAtBefore(
            InventoryStatus inventoryStatus,
            ListingStatus listingStatus,
            Instant createdAt
    );

    List<ListingTransaction>
    findAllByInventoryStatusAndListingStatusAndConfirmationStatusIsNull(
            InventoryStatus inventoryStatus,
            ListingStatus listingStatus
    );

    Optional<ListingTransaction> findByConfirmationToken(String token);

    List<ListingTransaction>
    findAllByConfirmationTokenIsNotNullAndConfirmationTokenCreatedAtBefore(
            Instant createdAt
    );


    List<ListingTransaction> findAllByInventorySellerId(Long sellerIf);

    List<ListingTransaction> findTop8ByInventoryStatusAndListingStatusOrderByCreatedAtDesc(InventoryStatus inventoryStatus,ListingStatus listingStatus);

    List<ListingTransaction> findTop10ByOrderByCreatedAtDesc();
    Optional<ListingTransaction> findFirstByInventorySellerIdAndListingStatusOrderByCreatedAtDesc(Long sellerId,ListingStatus listingStatus);
    Long countByInventorySellerIdAndInventoryStatusAndListingStatus(Long sellerId,InventoryStatus inventoryStatus,ListingStatus listingStatus);
    Optional<ListingTransaction> findByInventoryId(Long inventoryId);


    @Query(
            value = """
        SELECT lt
        FROM ListingTransaction lt
        WHERE lt.listingStatus = :listingStatus
          AND lt.inventory.status = :inventoryStatus
          AND lt.inventory.productCatalog.category.isActive = true
        """,
            countQuery = """
        SELECT COUNT(lt)
        FROM ListingTransaction lt
        WHERE lt.listingStatus = :listingStatus
          AND lt.inventory.status = :inventoryStatus
          AND lt.inventory.productCatalog.category.isActive = true
        """
    )
    @EntityGraph(attributePaths = {
            "inventory",
            "inventory.productCatalog",
            "inventory.productCatalog.category"
    })
    Page<ListingTransaction> findSitemapListings(
            @Param("listingStatus") ListingStatus listingStatus,
            @Param("inventoryStatus") InventoryStatus inventoryStatus,
            Pageable pageable
    );
}
