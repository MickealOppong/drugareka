package com.pages.repository;


import com.pages.util.Media;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
@Repository
public interface MediaRepo extends JpaRepository<Media,Long> {

    Optional<Media> findByFileName(String fileName);

    Optional<Media> findByPath(String path);

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM Media m WHERE m.path = :path")
    void deleteByPath(String path);

    List<Media> findAllByInventoryItemId(Long inventoryItemId);
    Optional<Media> findByInventoryItemId(Long inventoryItemId);
    Optional<Media> findByInventoryItemIdAndSortOrder(Long inventoryItemId,Integer sortOrder);
    Optional<Media> findByCategoryIdAndSortOrder(Long inventoryItemId,Integer sortOrder);
}

