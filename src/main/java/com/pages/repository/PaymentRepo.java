package com.pages.repository;

import com.pages.enums.PaymentProvider;
import com.pages.enums.PaymentStatus;
import com.pages.model.Payment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.security.Provider;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepo extends JpaRepository<Payment, Long> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<Payment> findByStripePaymentIntentId(String paymentIntent);

  Optional<Payment> findByStripeChargeId(String paymentIntent);

  Optional<Payment> findFirstByStripePaymentIntentIdAndStatus(
          String paymentIntent,
          PaymentStatus status
  );

  @Lock(LockModeType.PESSIMISTIC_WRITE)
Optional<Payment> findByOrderNumber(String orderNumber);
@Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<Payment> findByProviderSessionId(String sessionId);
Optional<Payment> findByListingOrderIdAndStatus(Long orderId, PaymentStatus paymentStatus);

  List<Payment> findByListingOrderId(Long orderId);
  Optional<Payment> findByProviderAndProviderSessionId(PaymentProvider provider,String sessionId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query("""
       SELECT p
       FROM Payment p
       WHERE p.providerSessionId = :sessionId
       """)
  Optional<Payment> findByProviderSessionIdForUpdate(
          @Param("sessionId") String sessionId
  );

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query("""
       SELECT p
       FROM Payment p
       WHERE p.id = :id
       """)
  Optional<Payment> findByIdForUpdate(
          @Param("id") Long id
  );

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query("""
       SELECT p
       FROM Payment p
       WHERE p.dispute.id = :disputeId
       """)
  Optional<Payment> findByDisputeIdForUpdate(Long disputeId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query("""
    SELECT p
    FROM Payment p
    WHERE p.status = :status
      AND p.provider = :provider
      AND p.createdAt < :cutoff
    """)
  List<Payment> findByStatusAndProviderAndCreatedAtBeforeForUpdate(
          @Param("status") PaymentStatus status,
          @Param("provider") PaymentProvider provider,
          @Param("cutoff") Instant cutoff
  );

}
