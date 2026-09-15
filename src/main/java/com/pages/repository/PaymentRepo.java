package com.pages.repository;

import com.pages.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentRepo extends JpaRepository<Payment, Long> {

Optional<Payment> findByStripeSessionId(String sessionId);
Optional<Payment> findByListingOrderId(Long orderId);
}
