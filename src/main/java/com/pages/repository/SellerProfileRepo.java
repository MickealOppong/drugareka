package com.pages.repository;

import com.pages.model.AppUser;
import com.pages.model.SellerProfile;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SellerProfileRepo extends JpaRepository<SellerProfile,Long> {

    Optional<SellerProfile> findByUser(AppUser user);
    Optional<SellerProfile> findByUserId(Long userId);
Optional<SellerProfile> findByStripeAccountId(String id);
    @Modifying
    @Query("UPDATE SellerProfile s SET s.currentYearSalesCount = 0, s.currentYearGrossVolumePln = 0.00")
    int resetAnnualDac7TaxCountersBulk();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT s FROM SellerProfile s WHERE s.id = :id")
    Optional<SellerProfile> findByIdForUpdate(Long id);

}
