package com.pages.repository;

import com.pages.model.CartItem;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface CartItemRepo extends JpaRepository<CartItem,Long> {

    Optional<CartItem> findByCartIdAndListingId(Long cartId, Long listingId);

    @Query("SELECT COUNT(c) FROM CartItem c WHERE c.cart.buyer.id = :buyerId")
    Long countItemsByCartBuyerId(Long buyerId);

    @Modifying
    @Query("DELETE FROM CartItem ci WHERE ci.cart.id = :cartId")
    void deleteAllByCartId(@Param("cartId") Long cartId);

    @Query("SELECT COUNT(c) FROM CartItem c WHERE c.cart.buyer.id = :buyerId")
    List<CartItem> findAllByCartBuyerId(Long buyerId);

    @Modifying
    @Query("DELETE FROM CartItem ci WHERE ci.reservedUntil < :now")
    int deleteByReservedUntilBefore(@Param("now") Instant now);
}
