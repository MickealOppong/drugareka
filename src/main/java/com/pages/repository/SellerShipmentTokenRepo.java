package com.pages.repository;

import com.pages.model.SellerShipmentToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SellerShipmentTokenRepo  extends JpaRepository<SellerShipmentToken,Long> {
    Optional<SellerShipmentToken> findByToken(String token);
}
