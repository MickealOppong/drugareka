package com.pages.repository;

import com.pages.model.AppUser;
import com.pages.model.ListingOrder;
import com.pages.model.ServiceCharge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ServiceChargeRepo extends JpaRepository<ServiceCharge,Long> {

    Optional<ServiceCharge> findByOrder(ListingOrder order);
    Optional<ServiceCharge> findByBuyer(AppUser buyer);

}
