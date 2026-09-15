package com.pages.repository;

import com.pages.model.InventoryItemPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryItemPriceRepo extends JpaRepository<InventoryItemPrice,Long> {

    Optional<InventoryItemPrice> findByInventoryItemId(Long id);

}
