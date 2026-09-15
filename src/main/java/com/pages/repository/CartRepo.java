package com.pages.repository;

import com.pages.enums.CartStatus;
import com.pages.model.AppUser;
import com.pages.model.Cart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CartRepo extends JpaRepository<Cart,Long> {

    Optional<Cart> findByBuyerIdAndStatus(Long buyerId, CartStatus cartStatus);
    void deleteAllByBuyerId(Long buyerId);
}
