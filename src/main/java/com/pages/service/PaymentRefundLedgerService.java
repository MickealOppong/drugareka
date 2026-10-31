package com.pages.service;


import com.pages.enums.OrderItemStatus;
import com.pages.enums.PaymentStatus;
import com.pages.exception.EntityNotFoundException;
import com.pages.exception.InvalidOperationException;
import com.pages.model.ListingOrder;
import com.pages.model.ListingOrderItem;
import com.pages.model.Payment;
import com.pages.repository.PaymentRepo;
import com.stripe.exception.StripeException;
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
    private final PayoutProcessingService payoutProcessingService;

    /**
     * Records a refund against the parent order and creates
     * the corresponding negative seller-payout entry.
     *
     * This method records the refund in the internal ledger;
     * it does not itself call the payment provider's refund API.
     */

    @Transactional
    public void createRefundFromSellerCancellation(ListingOrderItem item) throws StripeException {

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

        ListingOrder order = item.getListingOrder();
        List<ListingOrderItem> orderItems = order.getItems();

        if (orderItems == null || orderItems.isEmpty()) {
            throw new InvalidOperationException(
                    "Cannot refund an order without items."
            );
        }

        // Find the successful payment for this order.
        Payment originalPayment = paymentRepo
                .findByListingOrderIdAndStatus(
                        orderId,
                        PaymentStatus.PAID
                )
                .orElseThrow(() -> new EntityNotFoundException(
                        "No successful payment exists for order " + orderId
                ));

        // Determine the refund amount based on cancellation sequence.
        boolean isSingleItemOrder = orderItems.size() == 1;

        boolean isLastRemainingItem = orderItems.stream()
                .filter(orderItem ->
                        !Objects.equals(orderItem.getId(), item.getId()))
                .allMatch(orderItem ->
                        orderItem.getOrderItemStatus() == OrderItemStatus.CANCELLED
                );

        BigDecimal refundAmount;

        if (isSingleItemOrder) {

            //seller bare stripe charge
            BigDecimal stripeFee= payoutProcessingService.getStripeFee(originalPayment.getStripeChargeId());
            // Create the negative refund ledger entry.
            payoutProcessingService.createStripeFeeRefund(item,stripeFee);
            log.info("Stripe fee on payment:{}",stripeFee);
            // Single-item order: refund the full order total.
            refundAmount = order.getOrderTotal();



        } else if (isLastRemainingItem) {

            // Final item cancellation: include shipping.
            BigDecimal shippingCost = item.getShippingCost();
            BigDecimal serviceCharge = item.getServiceCharge();

            if (shippingCost == null || shippingCost.compareTo(BigDecimal.ZERO) == 0) {

                shippingCost = order.getItems().stream()
                        .filter(orderItem ->
                                Objects.equals(
                                        orderItem.getSeller().getId(),
                                        item.getSeller().getId()
                                ))
                        .map(ListingOrderItem::getShippingCost)
                        .filter(Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
            }

            if (serviceCharge == null || serviceCharge.compareTo(BigDecimal.ZERO) == 0) {

                serviceCharge = order.getItems().stream()
                        .filter(orderItem ->
                                Objects.equals(
                                        orderItem.getSeller().getId(),
                                        item.getSeller().getId()
                                ))
                        .map(ListingOrderItem::getServiceCharge)
                        .filter(Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

            }


            refundAmount = requestedRefund.add(shippingCost).add(serviceCharge);

            //seller bare stripe charge
            BigDecimal stripeFee= payoutProcessingService.getStripeFee(originalPayment.getStripeChargeId());
            BigDecimal shareOfStripeFee = payoutProcessingService.getSellerStripeFeeShare(stripeFee,order.getOrderTotal(),requestedRefund,shippingCost);
            // Create the negative refund ledger entry.
            payoutProcessingService.createStripeFeeRefund(item,shareOfStripeFee);

        } else {
            // Partial cancellation: refund this item's price only.
            refundAmount = requestedRefund;

            //seller bare stripe charge
            BigDecimal stripeFee= payoutProcessingService.getStripeFee(originalPayment.getStripeChargeId());
            BigDecimal shareOfStripeFee = payoutProcessingService.getSellerStripeFeeShare(stripeFee,order.getOrderTotal(),refundAmount,BigDecimal.ZERO);
            // Create the negative refund ledger entry.
            payoutProcessingService.createStripeFeeRefund(item,shareOfStripeFee);
        }

        if (refundAmount == null || refundAmount.signum() <= 0) {
            throw new InvalidOperationException(
                    "Refund amount must be positive."
            );
        }

        // Calculate the remaining local balance from positive payments
        // and negative refund ledger entries.
        List<Payment> payments =
                paymentRepo.findByListingOrderId(orderId);

        BigDecimal netPaidBalance = payments.stream()
                .filter(p -> p.getStatus() == PaymentStatus.PAID
                        || p.getStatus() == PaymentStatus.AWAITING_REFUND
                        || p.getStatus() == PaymentStatus.REFUNDED)
                .map(Payment::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (netPaidBalance.signum() <= 0
                || refundAmount.compareTo(netPaidBalance) > 0) {
            log.warn(
                    "Refund rejected for item {}: requested={}, remaining={}",
                    item.getId(),
                    refundAmount,
                    netPaidBalance
            );

            throw new InvalidOperationException(
                    "Refund exceeds the remaining refundable balance."
            );
        }

        // Create the negative refund ledger entry.
        Payment refund = Payment.builder()
                .listingOrder(order)
                .status(PaymentStatus.AWAITING_REFUND)
                .amount(refundAmount.negate())
                .provider(originalPayment.getProvider())
                .currency(originalPayment.getCurrency())
                .providerSessionId(originalPayment.getProviderSessionId())
                .stripePaymentIntentId(
                        originalPayment.getStripePaymentIntentId()
                )
                .orderNumber(originalPayment.getOrderNumber())
                .build();

        paymentRepo.save(refund);

        // Keep the payout adjustment in the same database transaction.
        sellerPayoutService.createRefund(item);
        log.info(
                "Refund ledger entry created for item {}: amount={}",
                item.getId(),
                refundAmount
        );
    }

    /**
     * Records a refund against the parent order and creates
     * the corresponding negative seller-payout entry.
     *
     * This method records the refund in the internal ledger;
     * it does not itself call the payment provider's refund API.
     */

    @Transactional
    public void createBuyerRefund(ListingOrderItem item) throws StripeException {

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

        ListingOrder order = item.getListingOrder();
        List<ListingOrderItem> orderItems = order.getItems();

        if (orderItems == null || orderItems.isEmpty()) {
            throw new InvalidOperationException(
                    "Cannot refund an order without items."
            );
        }

        // Find the successful payment for this order.
        Payment originalPayment = paymentRepo
                .findByListingOrderIdAndStatus(
                        orderId,
                        PaymentStatus.PAID
                )
                .orElseThrow(() -> new EntityNotFoundException(
                        "No successful payment exists for order " + orderId
                ));

        // Determine the refund amount based on cancellation sequence.
        int itemCount = orderItems.size();

        boolean isAllCancelled = orderItems.stream()
                .allMatch(orderItem ->
                        orderItem.getOrderItemStatus() == OrderItemStatus.CANCELLED
                );

        BigDecimal refundAmount;

        if (itemCount == 1) {

            // Single-item order: refund the full order total.
            refundAmount = order.getOrderTotal();



        } else if (isAllCancelled) {
            // Final item cancellation: include shipping.
            BigDecimal shippingCost = item.getShippingCost();

            if (shippingCost == null) {
                shippingCost = BigDecimal.ZERO;
            }

            refundAmount = requestedRefund.add(shippingCost);


        } else {
            // Partial cancellation: refund this item's price only.
            refundAmount = requestedRefund;
        }

        if (refundAmount == null || refundAmount.signum() <= 0) {
            throw new InvalidOperationException(
                    "Refund amount must be positive."
            );
        }

        // Calculate the remaining local balance from positive payments
        // and negative refund ledger entries.
        List<Payment> payments =
                paymentRepo.findByListingOrderId(orderId);

        BigDecimal netPaidBalance = payments.stream()
                .filter(p -> p.getStatus() == PaymentStatus.PAID
                        || p.getStatus() == PaymentStatus.AWAITING_REFUND
                        || p.getStatus() == PaymentStatus.REFUNDED)
                .map(Payment::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (netPaidBalance.signum() <= 0
                || refundAmount.compareTo(netPaidBalance) > 0) {
            log.warn(
                    "Refund rejected for item {}: requested={}, remaining={}",
                    item.getId(),
                    refundAmount,
                    netPaidBalance
            );

            throw new InvalidOperationException(
                    "Refund exceeds the remaining refundable balance."
            );
        }

        // Create the negative refund ledger entry.
        Payment refund = Payment.builder()
                .listingOrder(order)
                .status(PaymentStatus.AWAITING_REFUND)
                .amount(refundAmount.negate())
                .provider(originalPayment.getProvider())
                .currency(originalPayment.getCurrency())
                .providerSessionId(originalPayment.getProviderSessionId())
                .stripePaymentIntentId(
                        originalPayment.getStripePaymentIntentId()
                )
                .build();

        paymentRepo.save(refund);

    }

}

