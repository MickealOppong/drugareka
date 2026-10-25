package com.pages.service;


import com.pages.enums.PaymentStatus;
import com.pages.exception.EntityNotFoundException;
import com.pages.exception.InvalidOperationException;
import com.pages.model.ListingOrderItem;
import com.pages.model.Payment;
import com.pages.repository.PaymentRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentRefundLedgerService {

    private final PaymentRepo paymentRepo;
    private final SellerPayoutService sellerPayoutService;

    /**
     * Records a refund against the parent order and creates
     * the corresponding negative seller-payout entry.
     *
     * This method records the refund in the internal ledger;
     * it does not itself call the payment provider's refund API.
     */
    @Transactional
    public void createRefund(ListingOrderItem item) {

        if (item == null || item.getListingOrder() == null) {
            throw new InvalidOperationException(
                    "Cannot refund an item without an associated order."
            );
        }

        BigDecimal requestedRefund = item.getSellerPrice();

        if (requestedRefund == null || requestedRefund.signum() <= 0) {
            throw new InvalidOperationException(
                    "Refund amount must be positive."
            );
        }

        Long orderId = item.getListingOrder().getId();

        List<Payment> payments =
                paymentRepo.findByListingOrderId(orderId);

        BigDecimal netPaidBalance = payments.stream()
                .filter(payment ->
                        payment.getStatus() == PaymentStatus.PAID
                                || payment.getStatus() == PaymentStatus.AWAITING_REFUND)
                .map(Payment::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (netPaidBalance.signum() <= 0
                || requestedRefund.compareTo(netPaidBalance) > 0) {
            log.warn(
                    "Refund rejected for item {}: requested={}, remaining={}",
                    item.getId(),
                    requestedRefund,
                    netPaidBalance
            );

            throw new InvalidOperationException(
                    "Refund exceeds the remaining refundable balance."
            );
        }

        Payment originalPayment = paymentRepo
                .findByListingOrderIdAndStatus(
                        orderId,
                        PaymentStatus.PAID
                )
                .orElseThrow(() -> new EntityNotFoundException(
                        "No successful payment exists for order " + orderId
                ));

        Payment refund = Payment.builder()
                .listingOrder(item.getListingOrder())
                .status(PaymentStatus.AWAITING_REFUND)
                .amount(requestedRefund.negate())
                .provider(originalPayment.getProvider())
                .currency(originalPayment.getCurrency())
                .providerSessionId(originalPayment.getProviderSessionId())
                .stripePaymentIntentId(
                        originalPayment.getStripePaymentIntentId()
                )
                .build();

        paymentRepo.save(refund);

        // Keep the ledger and payout adjustment in the same transaction.
        sellerPayoutService.createRefund(item);

        log.info(
                "Refund ledger entry created for item {}: {}",
                item.getId(),
                requestedRefund
        );
    }
}

