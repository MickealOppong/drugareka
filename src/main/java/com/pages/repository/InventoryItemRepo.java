package com.pages.repository;

import com.pages.enums.InventoryStatus;
import com.pages.model.InventoryItem;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryItemRepo extends JpaRepository<InventoryItem,Long> {

    List<InventoryItem> findBySeller(Long sellerId);
    List<InventoryItem> findByStatus(String status);
    Optional<InventoryItem> findByIdAndStatus(Long id, InventoryStatus status);
    Optional<InventoryItem> findById(Long id, InventoryStatus status);

    Optional<InventoryItem> findByProductCatalogName(String productName);
        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("""
        SELECT i
        FROM InventoryItem i
        WHERE i.id = :id
    """)
        Optional<InventoryItem> findByIdForUpdate(@Param("id") Long id);

    List<InventoryItem> findByStatusAndReservedUntilBefore(InventoryStatus status, Instant time);

    List<InventoryItem> findByStatusAndReservedBy(InventoryStatus status,Long reservedBy);
}
