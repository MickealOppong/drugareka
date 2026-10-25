package com.pages.repository;

import com.pages.model.SellerShipmentToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SellerShipmentTokenRepo  extends JpaRepository<SellerShipmentToken,Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("""
       SELECT t
       FROM SellerShipmentToken t
       WHERE t.token = :token
       """)
    Optional<SellerShipmentToken> findByTokenForUpdate(String token);

    Optional<SellerShipmentToken> findByToken(String token);
}
