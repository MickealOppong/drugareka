package com.pages.repository;

import com.pages.enums.ItemSize;
import com.pages.enums.ShippingMethod;
import com.pages.util.ShippingOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;

@Repository
public interface ShippingOptionRepo extends JpaRepository<ShippingOption, Long> {

    Optional<ShippingOption> findByItemSizeAndActiveTrue(ItemSize itemSize);
    Optional<ShippingOption> findByItemSizeAndShippingMethodAndActiveTrue(ItemSize itemSize, ShippingMethod shippingMethod);
    Optional<ShippingOption> findByItemSizeAndShippingMethod(ItemSize itemSize, ShippingMethod shippingMethod);
}
