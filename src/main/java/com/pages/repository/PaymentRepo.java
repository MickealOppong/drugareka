package com.pages.repository;

import com.pages.enums.PaymentProvider;
import com.pages.enums.PaymentStatus;
import com.pages.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepo extends JpaRepository<Payment, Long> {

Optional<Payment> findByProviderSessionId(String sessionId);
Optional<Payment> findByListingOrderIdAndStatus(Long orderId, PaymentStatus paymentStatus);

  List<Payment> findByListingOrderId(Long orderId);
  Optional<Payment> findByProviderAndProviderSessionId(PaymentProvider provider,String sessionId);

}
