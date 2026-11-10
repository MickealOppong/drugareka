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
    public void createRefundFromSellerCancellation(ListingOrderItem item)
            throws StripeException {

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

        ListingOrder order = item.getListingOrder();
        Long orderId = order.getId();

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

        // Determine cancellation sequence.
        boolean isSingleItemOrder = orderItems.size() == 1;

        boolean isLastRemainingItem = orderItems.stream()
                .filter(orderItem ->
                        !Objects.equals(orderItem.getId(), item.getId()))
                .allMatch(orderItem ->
                        orderItem.getOrderItemStatus() == OrderItemStatus.CANCELLED
                );

        BigDecimal shippingCost = BigDecimal.ZERO;
        BigDecimal serviceCharge = BigDecimal.ZERO;

        BigDecimal refundAmount;

        if (isSingleItemOrder) {

            // Full seller cancellation: refund the entire order total.
            refundAmount = order.getOrderTotal();

            shippingCost = item.getShippingCost() != null
                    ? item.getShippingCost()
                    : BigDecimal.ZERO;

            serviceCharge = item.getServiceCharge() != null
                    ? item.getServiceCharge()
                    : BigDecimal.ZERO;

        } else if (isLastRemainingItem) {

            // Final remaining item: refund item price, shipping and service charge.
            shippingCost = item.getShippingCost();
            serviceCharge = item.getServiceCharge();

            // Fallback: calculate shipping for this seller if not stored on item.
            if (shippingCost == null
                    || shippingCost.compareTo(BigDecimal.ZERO) == 0) {

                shippingCost = orderItems.stream()
                        .filter(orderItem ->
                                Objects.equals(
                                        orderItem.getSeller().getId(),
                                        item.getSeller().getId()
                                ))
                        .map(ListingOrderItem::getShippingCost)
                        .filter(Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
            }

            // Fallback: calculate service charges for this seller.
            if (serviceCharge == null
                    || serviceCharge.compareTo(BigDecimal.ZERO) == 0) {

                serviceCharge = orderItems.stream()
                        .filter(orderItem ->
                                Objects.equals(
                                        orderItem.getSeller().getId(),
                                        item.getSeller().getId()
                                ))
                        .map(ListingOrderItem::getServiceCharge)
                        .filter(Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
            }

            shippingCost = shippingCost != null
                    ? shippingCost
                    : BigDecimal.ZERO;

            serviceCharge = serviceCharge != null
                    ? serviceCharge
                    : BigDecimal.ZERO;

            refundAmount = requestedRefund
                    .add(shippingCost)
                    .add(serviceCharge);

        } else {

            // Partial cancellation: refund this item's price only.
            refundAmount = requestedRefund;
        }

        if (refundAmount == null || refundAmount.signum() <= 0) {
            throw new InvalidOperationException(
                    "Refund amount must be positive."
            );
        }

        // Seller bears the allocated Stripe processing fee,
        // regardless of delivery method.
        BigDecimal stripeFee =
                payoutProcessingService.getStripeFee(
                        originalPayment.getStripeChargeId()
                );

        BigDecimal stripeFeeShare;

        if (isSingleItemOrder) {

            stripeFeeShare = stripeFee;

        } else if (isLastRemainingItem) {

            stripeFeeShare =
                    payoutProcessingService.getSellerStripeFeeShare(
                            stripeFee,
                            order.getOrderTotal(),
                            requestedRefund,
                            shippingCost
                    );

        } else {

            stripeFeeShare =
                    payoutProcessingService.getSellerStripeFeeShare(
                            stripeFee,
                            order.getOrderTotal(),
                            refundAmount,
                            BigDecimal.ZERO
                    );
        }

        payoutProcessingService.createStripeFeeRefund(
                item,
                stripeFeeShare
        );

        log.info(
                "Seller Stripe fee allocation for item {}: {}",
                item.getId(),
                stripeFeeShare
        );

        log.info(
                "Seller cancellation refund calculation: item={}, itemPrice={}, shipping={}, serviceCharge={}, totalRefund={}",
                item.getId(),
                requestedRefund,
                shippingCost,
                serviceCharge,
                refundAmount
        );

        // Calculate remaining local balance from payments and refunds.
        List<Payment> payments =
                paymentRepo.findByListingOrderId(orderId);

        BigDecimal netPaidBalance = payments.stream()
                .filter(p ->
                        p.getStatus() == PaymentStatus.PAID
                                || p.getStatus() == PaymentStatus.AWAITING_REFUND
                                || p.getStatus() == PaymentStatus.REFUNDED
                )
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

        // Create negative refund ledger entry.
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

        // Adjust seller payout and allocate seller cancellation costs.
        sellerPayoutService.createRefund(item);

        log.info(
                "Refund ledger entry created for item {}: amount={}",
                item.getId(),
                refundAmount
        );
    }

    /**
     *  BUYER RETURN DOES NOT REFUND SERVICE CHARGE
     * @param item
     * @throws StripeException
     */
    @Transactional
    public void createRefundFromReturn(ListingOrderItem item)
            throws StripeException {

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

        ListingOrder order = item.getListingOrder();
        Long orderId = order.getId();

        List<ListingOrderItem> orderItems = order.getItems();

        if (orderItems == null || orderItems.isEmpty()) {
            throw new InvalidOperationException(
                    "Cannot refund an order without items."
            );
        }

        // Find the original successful payment.
        Payment originalPayment = paymentRepo
                .findByListingOrderIdAndStatus(
                        orderId,
                        PaymentStatus.PAID
                )
                .orElseThrow(() -> new EntityNotFoundException(
                        "No successful payment exists for order " + orderId
                ));

        // A return after fulfilment refunds the item price only.
        // Kasoa service charge and completed shipping are excluded.

        if (requestedRefund.signum() <= 0) {
            throw new InvalidOperationException(
                    "Refund amount must be positive."
            );
        }

        // Calculate Stripe processing fee allocation.
        BigDecimal stripeFee =
                payoutProcessingService.getStripeFee(
                        originalPayment.getStripeChargeId()
                );

        BigDecimal stripeFeeShare =
                payoutProcessingService.getSellerStripeFeeShare(
                        stripeFee,
                        order.getOrderTotal(),
                        requestedRefund,
                        BigDecimal.ZERO
                );

        payoutProcessingService.createStripeFeeRefund(
                item,
                stripeFeeShare
        );

        log.info(
                "Return refund calculation: item={}, itemPrice={}, serviceCharge={}, shipping={}, totalRefund={}",
                item.getId(),
                requestedRefund,
                item.getServiceCharge(),
                item.getShippingCost(),
                requestedRefund
        );

        log.info(
                "Seller Stripe fee allocation for returned item {}: {}",
                item.getId(),
                stripeFeeShare
        );

        // Calculate remaining refundable balance.
        List<Payment> payments =
                paymentRepo.findByListingOrderId(orderId);

        BigDecimal netPaidBalance = payments.stream()
                .filter(p ->
                        p.getStatus() == PaymentStatus.PAID
                                || p.getStatus() == PaymentStatus.AWAITING_REFUND
                                || p.getStatus() == PaymentStatus.REFUNDED
                )
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

        // Create negative refund ledger entry.
        Payment refund = Payment.builder()
                .listingOrder(order)
                .status(PaymentStatus.AWAITING_REFUND)
                .amount(requestedRefund.negate())
                .provider(originalPayment.getProvider())
                .currency(originalPayment.getCurrency())
                .providerSessionId(originalPayment.getProviderSessionId())
                .stripePaymentIntentId(
                        originalPayment.getStripePaymentIntentId()
                )
                .orderNumber(originalPayment.getOrderNumber())
                .build();

        paymentRepo.save(refund);

        // Adjust seller payout.
        sellerPayoutService.createRefund(item);

        log.info(
                "Return refund ledger entry created for item {}: amount={}",
                item.getId(),
                requestedRefund
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

