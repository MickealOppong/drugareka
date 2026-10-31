package com.pages.service;

import com.pages.enums.*;
import com.pages.exception.EntityNotFoundException;
import com.pages.model.*;
import com.pages.repository.PaymentRepo;
import com.pages.repository.PayoutExceptionRepo;
import com.pages.repository.SellerPayoutRepo;
import com.stripe.exception.StripeException;
import com.stripe.model.*;
import com.stripe.net.RequestOptions;
import com.stripe.param.ChargeRetrieveParams;
import com.stripe.param.RefundCreateParams;
import com.stripe.param.TransferCreateParams;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Ref;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

@Slf4j
@Service
@RequiredArgsConstructor
public class PayoutProcessingService {



    private final SellerPayoutRepo sellerPayoutRepo;
    private final PaymentRepo paymentRepo;
    private final PayoutExceptionRepo payoutExceptionRepo;
    private final EmailNotificationService emailNotificationService;
    private final ServiceChargeService serviceChargeService;




    @Transactional
    public void createForReview(SellerPayout payout, PayoutExceptionReason reason,String msg) {
        try {

            PayoutException payoutException = PayoutException.builder()
                    .exceptionReason(reason)
                    .seller(payout.getSeller())
                    .sellerPayout(payout)
                    .stripeMessages(msg)
                    .status(PayoutExceptionStatus.PENDING_REVIEW)
                    .build();
             payoutExceptionRepo.save(payoutException);

        } catch (Exception e) {
            log.error(
                    "Failed to create payout exception for payout {}",
                    payout.getId(),
                    e
            );
            throw e;
        }
    }


    /**
     * Calculates the seller's proportional share of the actual Stripe fee.
     *
     * @param stripeFee actual Stripe processing fee
     * @param checkoutTotal total amount charged to the buyer
     * @param sellerItemValue seller's gross payout before Stripe fee deduction
     * @param shippingCost courier charge related to order
     * @return seller's share of the Stripe fee, rounded to two decimal places
     */
    public BigDecimal getSellerStripeFeeShare(
            BigDecimal stripeFee,
            BigDecimal checkoutTotal,
            BigDecimal sellerItemValue,
            BigDecimal shippingCost) {

        if (stripeFee == null || checkoutTotal == null
                || sellerItemValue == null || shippingCost == null) {
            throw new IllegalArgumentException(
                    "Stripe fee, checkout total, item value, and shipping cost are required."
            );
        }

        if (checkoutTotal.signum() <= 0
                || stripeFee.signum() < 0
                || sellerItemValue.signum() < 0
                || shippingCost.signum() < 0) {
            throw new IllegalArgumentException(
                    "Amounts must be non-negative and checkout total must be positive."
            );
        }

        BigDecimal sellerFeeBase = sellerItemValue.add(shippingCost);

        return stripeFee
                .multiply(sellerFeeBase)
                .divide(checkoutTotal, 10, RoundingMode.HALF_UP)
                .setScale(2, RoundingMode.HALF_UP);
    }



    /**
     * Retreive stripe fee for a checkout charge
     * @param stripeChargeId
     * @return
     * @throws StripeException
     */
    public BigDecimal getStripeFee(String stripeChargeId)
            throws StripeException {

        ChargeRetrieveParams params = ChargeRetrieveParams.builder()
                .addExpand("balance_transaction")
                .build();

        Charge charge = Charge.retrieve(
                stripeChargeId,
                params,
                null
        );

        BalanceTransaction balanceTransaction =
                charge.getBalanceTransactionObject();

        if (balanceTransaction == null || balanceTransaction.getFee() == null) {
            throw new IllegalStateException(
                    "Stripe balance transaction or fee is not available yet."
            );
        }

        // Stripe returns the fee in the currency's smallest unit.
        return BigDecimal.valueOf(balanceTransaction.getFee())
                .movePointLeft(2)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Deducts the seller's proportional share of the Stripe processing fee
     * before transferring the payout.
     *
     * @param stripeFee actual Stripe processing fee
     * @param checkoutTotal total amount charged to the buyer
     * @param sellerItemValue seller's gross payout before fee deduction
     * @param shippingCost courier charge related to order
     * @return seller's net payout after their share of the Stripe fee
     */
    private BigDecimal getSellerNetAmountAfterStripeFee(
            BigDecimal stripeFee,
            BigDecimal checkoutTotal,
            BigDecimal sellerItemValue,
            BigDecimal shippingCost) {

        BigDecimal sellerStripeFeeShare = getSellerStripeFeeShare(
                stripeFee,
                checkoutTotal,
                sellerItemValue,
                shippingCost
        );

        return sellerItemValue
                .subtract(sellerStripeFeeShare)
                .setScale(2, RoundingMode.HALF_UP);
    }


    @Transactional
    public void confirmReceiptAndReleaseFunds() {

        List<SellerPayout> approvedPayouts =
                sellerPayoutRepo.findByStatus(PayoutStatus.APPROVED);

        log.info("approved payouts count:{}",approvedPayouts.size());

        if (approvedPayouts.isEmpty()) {
            log.info("Payout engine: no approved payouts to process");
            return;
        }

        for (SellerPayout candidate : approvedPayouts) {
            Long payoutId = candidate.getId();

            try {
                // Atomically claim the payout in a separate transaction.
                SellerPayout payout = claimApprovedPayout(payoutId);

                if (payout == null) {
                    // Another worker may already have claimed it.
                    continue;
                }

                ListingOrderItem item = payout.getListingOrderItem();

                Payment payment = paymentRepo
                        .findByListingOrderIdAndStatus(
                                item.getListingOrder().getId(),
                                PaymentStatus.PAID
                        )
                        .orElseThrow(() -> new IllegalStateException(
                                "No successful payment for payout " + payoutId
                        ));

                String chargeId = payment.getStripeChargeId();

                //get stripe fee associated with this charge
               BigDecimal stripeFee =  getStripeFee(chargeId);

                if (chargeId == null || (!chargeId.startsWith("ch_") && !chargeId.startsWith("py_"))) {
                    throw new IllegalStateException(
                            "Valid Stripe Charge ID is missing for payout " + payoutId
                    );
                }

                String accountId = item.getSeller().getStripeAccountId();

                if (accountId == null || accountId.isBlank()) {
                    String reason = "Seller has no Stripe Connect account for payout " + payoutId;

                    createForReview(payout, PayoutExceptionReason.SELLER_ONBOARDING_INCOMPLETE,reason);
                    returnToApproved(payoutId);
                  continue;
                }

                //get service charge associate with this order

                log.info("Stripe fee :{} charge id :{} original payment :{} seller Gross payout:{}",stripeFee,chargeId,payment.getAmount(),item.getSellerPrice());

                BigDecimal sellerNetPayout = getSellerNetAmountAfterStripeFee(stripeFee,payment.getAmount(),item.getSellerPrice(),item.getShippingCost());

                //get any unpaid stripe fee
                BigDecimal outStandingFeeToRecover = sellerPayoutRepo.findAllByStatus(PayoutStatus.CHARGE_BACK).stream().map(SellerPayout::getAmount)
                        .reduce(BigDecimal.ZERO,BigDecimal::add);

                log.info("net payout :{} outstanding fee :{}",sellerNetPayout,outStandingFeeToRecover.negate());

                long amountInGrosz = getAmountInGrosz(sellerNetPayout, outStandingFeeToRecover, payoutId);

                log.info("to pay seller after stripe fee :{} outstanding fee :{}",amountInGrosz,outStandingFeeToRecover.negate());
                long availableBalance = getAvailablePlnBalanceInGrosz();

                if (availableBalance < amountInGrosz) {
                    log.warn(
                            "Insufficient Stripe PLN balance for payout # {}. Available: {} grosz, required: {} grosz",
                            payoutId,
                            availableBalance,
                            amountInGrosz
                    );
                    createForReview(payout,PayoutExceptionReason.INSUFFICIENT_STRIPE_BALANCE,"Insufficient balance");

                    // Revert the row status inside the inner atomic connection framework
                    returnToApproved(payoutId);
                    continue; // Safely skip to the next candidate item in the payout list
                }

                String orderNumber = item.getListingOrder().getOrderNumber();

                TransferCreateParams params = TransferCreateParams.builder()
                        .setAmount(amountInGrosz)
                        .setCurrency("pln")
                        .setDestination(accountId)
                        .setSourceTransaction(chargeId)
                        .setTransferGroup(orderNumber)
                        .putMetadata("seller_payout_id", payoutId.toString())
                        .build();

                RequestOptions options = RequestOptions.builder()
                        .setIdempotencyKey("seller-payout-item-" + payoutId)
                        .build();

                Transfer transfer = Transfer.create(params);

                // Persist the terminal success token in a new, short database transaction.
                markTransferred(
                        payoutId,
                        transfer.getId()
                );

                String email = item.getSeller().getUser().getUsername();
                emailNotificationService.sendTransferNotification(email,item.getSellerPrice(),orderNumber,transfer.getId());

                log.info(
                        "Payout engine: payout {} transferred successfully; transferId={}",
                        payoutId,
                        transfer.getId()
                );

            } catch (Exception e) {
                log.error(
                        "Payout engine: failed to process payout {} exception: {}",
                        payoutId,
                        e.getMessage()
                );


                // the committed database state back to APPROVED for automated retries.
                try {
                    returnToApproved(payoutId);
                    log.info("Payout engine: successfully rolled back database row status to APPROVED for ID: {}", payoutId);
                } catch (Exception rollbackError) {
                    log.error("Payout engine: critical failure attempting to return payout {} to APPROVED state: {}",
                            payoutId, rollbackError.getMessage());
                }

                // Continue gracefully with other pending payouts in the loop iteration sequence
            }
        }
    }

    public static long getAmountInGrosz(BigDecimal sellerNetPayout, BigDecimal outStandingFeeToRecover, Long payoutId) {
        BigDecimal sellerNetPayoutAfterDeductingStripeFeeOnCancelledOrders = sellerNetPayout.add(outStandingFeeToRecover);

        if (sellerNetPayoutAfterDeductingStripeFeeOnCancelledOrders.signum() <= 0) {
            throw new IllegalStateException(
                    "Invalid payout amount for payout " + payoutId
            );
        }

        return sellerNetPayoutAfterDeductingStripeFeeOnCancelledOrders
                .movePointRight(2)
                .longValueExact();
    }

    private  void createPayoutRefund(ListingOrderItem item){
        SellerPayout payout = SellerPayout.builder()
                .listingOrderItem(item)
                .seller(item.getSeller())
                .amount(item.getSellerPrice().negate())
                .sellerNameSnapshot(item.getSellerNameSnapshot())
                .currency("PLN")
                .status(PayoutStatus.REFUND)
                .build();

         sellerPayoutRepo.save(payout);
    }

    public   void createStripeFeeRefund(ListingOrderItem item,BigDecimal fee){
        SellerPayout payout = SellerPayout.builder()
                .listingOrderItem(item)
                .seller(item.getSeller())
                .amount(fee.negate())
                .sellerNameSnapshot(item.getSellerNameSnapshot())
                .currency("PLN")
                .status(PayoutStatus.CHARGE_BACK)
                .build();

        sellerPayoutRepo.save(payout);
    }

    private  void createStripeFeeRelatedToOrderCancellation(ListingOrderItem item,BigDecimal stripeFee){
        SellerPayout payout = SellerPayout.builder()
                .listingOrderItem(item)
                .seller(item.getSeller())
                .amount(stripeFee.negate())
                .sellerNameSnapshot(item.getSellerNameSnapshot())
                .currency("PLN")
                .status(PayoutStatus.CHARGE_BACK)
                .build();

        sellerPayoutRepo.save(payout);
    }


    @Transactional
    public void createRefundForUnconfirmedOrderItem(Payment payment,ListingOrderItem item){


        Payment refund = Payment.builder()
                .listingOrder(item.getListingOrder())
                .status(PaymentStatus.AWAITING_REFUND)
                .amount((item.getSellerPrice()).negate())
                .currency("PLN")
                .providerSessionId(payment.getProviderSessionId())
                .stripePaymentIntentId(payment.getStripePaymentIntentId())
                .orderNumber(item.getListingOrder().getOrderNumber())
                .provider(PaymentProvider.STRIPE)
                .build();

        paymentRepo.save(refund);

        //adjust seller payout for refund
        createPayoutRefund(item);


        //send email confirmation
        String email = item.getListingOrder().getBuyer().getUsername();
        String name = item.getListingOrder().getBuyer().getFirstName();
        String orderNumber = item.getListingOrder().getOrderNumber();
        String itemName = item.getProductNameSnapshot();
        BigDecimal amount = item.getSellerPrice();
        emailNotificationService.sendOrderCancellationRefundEmailToBuyer(email,name,orderNumber,itemName,amount);
    }

    public Refund processStripeOrderRefund(Payment refund)
            throws StripeException {

        try {
            long amountInGrosze = refund.getAmount()
                    .abs()
                    .setScale(2, RoundingMode.HALF_UP)
                    .movePointRight(2)
                    .longValueExact();

            Map<String, String> metadata = new HashMap<>();
            metadata.put("payment_ledger_id", String.valueOf(refund.getId()));

            RefundCreateParams refundParams = RefundCreateParams.builder()
                    .setPaymentIntent(refund.getStripePaymentIntentId())
                    .setReason(RefundCreateParams.Reason.REQUESTED_BY_CUSTOMER)
                    .setAmount(amountInGrosze)
                    .setMetadata(metadata)
                    .build();

            Refund stripeRefund = Refund.create(refundParams);


            log.info(
                    "Stripe refund created: {} grosze, Payment ID: {}, Stripe Refund ID: {}",
                    amountInGrosze,
                    refund.getId(),
                    stripeRefund.getId()
            );
            return stripeRefund;

        } catch (StripeException e) {
            log.error(
                    "Stripe refund failed for Payment ID: {}",
                    refund.getId(),
                    e
            );

            throw e;
        }
    }

    public Refund processStripeSingleIsolatedRefund(Payment refund,BigDecimal amount)
            throws StripeException {

        try {
            long amountInGrosze =amount
                    .abs()
                    .setScale(2, RoundingMode.HALF_UP)
                    .movePointRight(2)
                    .longValueExact();

            Map<String, String> metadata = new HashMap<>();
            metadata.put("payment_ledger_id", String.valueOf(refund.getId()));

            RefundCreateParams refundParams = RefundCreateParams.builder()
                    .setPaymentIntent(refund.getStripePaymentIntentId())
                    .setReason(RefundCreateParams.Reason.REQUESTED_BY_CUSTOMER)
                    .setAmount(amountInGrosze)
                    .setMetadata(metadata)
                    .build();

            Refund stripeRefund = Refund.create(refundParams);


            log.info(
                    "Stripe refund created: {} grosze, Payment ID: {}, Stripe Refund ID: {}",
                    amountInGrosze,
                    refund.getId(),
                    stripeRefund.getId()
            );
            return stripeRefund;

        } catch (StripeException e) {
            log.error(
                    "Stripe refund failed for Payment ID: {}",
                    refund.getId(),
                    e
            );

            throw e;
        }
    }

    @Transactional
    public void refundForUnconfirmedOrder(Payment payment) {

        Payment refund = Payment.builder()
                .listingOrder(payment.getListingOrder())
                .status(PaymentStatus.AWAITING_REFUND)
                .amount(payment.getAmount().negate())
                .currency("PLN")
                .providerSessionId(payment.getProviderSessionId())
                .stripePaymentIntentId(payment.getStripePaymentIntentId())
                .orderNumber(payment.getOrderNumber())
                .provider(PaymentProvider.STRIPE)
                .build();

        paymentRepo.save(refund);

        for( ListingOrderItem item :payment.getListingOrder().getItems()){

            //refund payout
            String sellerName = item.getSellerNameSnapshot();

            SellerPayout payout = SellerPayout.builder()
                    .listingOrderItem(item)
                    .seller(item.getSeller())
                    .amount((item.getSellerPrice().add(item.getShippingCost())).negate())
                    .sellerNameSnapshot(sellerName)
                    .currency("PLN")
                    .status(PayoutStatus.REFUND)
                    .build();

            sellerPayoutRepo.save(payout);

            //reverse service charge earned
            serviceChargeService.reverseCharge(item);


            //send email confirmation
            String email = item.getListingOrder().getBuyer().getUsername();
            String name = item.getListingOrder().getBuyer().getFirstName();
            String orderNumber = item.getListingOrder().getOrderNumber();
            String itemName = item.getProductNameSnapshot();
            emailNotificationService.sendOrderCancellationRefundEmailToBuyer(email,name,orderNumber,itemName,item.getSellerPrice());

        }

    }
    @Transactional
    public void returnToApproved(Long payoutId){
        SellerPayout payout = sellerPayoutRepo.findById(payoutId).orElse(null);
        if(payout!=null){
            payout.setStatus(PayoutStatus.APPROVED);
            sellerPayoutRepo.save(payout);
        }

    }

    @Transactional
    public SellerPayout claimApprovedPayout(Long payoutId) {

        SellerPayout payout = sellerPayoutRepo
                .findByIdForUpdate(payoutId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Payout not found: " + payoutId
                ));

        if (payout.getStatus() != PayoutStatus.APPROVED) {
            return null;
        }

        payout.setStatus(PayoutStatus.PROCESSING);

        sellerPayoutRepo.save(payout);

        return payout;
    }

    @Transactional
    public void approvedPayout(ListingOrderItem item) {


        SellerPayout payout = sellerPayoutRepo
                .findByListingOrderItemId(item.getId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Payout not found: " + item.getId()
                ));

        if((item.getOrderItemStatus() !=OrderItemStatus.PAID ||item.getReceiptConfirmationStatus()!= ReceiptConfirmationStatus.CONFIRMED)){
            return;
        }
        if (payout.getStatus() == PayoutStatus.APPROVED
                || payout.getStatus() == PayoutStatus.PROCESSING
                || payout.getStatus() == PayoutStatus.TRANSFERRED) {
            return;
        }

        payout.setStatus(PayoutStatus.APPROVED);

        sellerPayoutRepo.save(payout);

    }

    @Transactional
    public void markTransferred(Long payoutId, String transferId) {

        SellerPayout payout = sellerPayoutRepo
                .findByIdForUpdate(payoutId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Payout not found: " + payoutId
                ));

        if (payout.getStatus() == PayoutStatus.TRANSFERRED
                && transferId.equals(payout.getTransferId())) {
            return;
        }

        if (payout.getStatus() != PayoutStatus.PROCESSING) {
            throw new IllegalStateException(
                    "Payout is not processing: " + payoutId
            );
        }

        payout.setTransferId(transferId);
        payout.setStatus(PayoutStatus.TRANSFERRED);

        sellerPayoutRepo.save(payout);
    }

    @Transactional
    public void markProcessing(Long payoutId, String transferId) {

        SellerPayout payout = sellerPayoutRepo
                .findByIdForUpdate(payoutId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Payout not found: " + payoutId
                ));

        if (payout.getStatus() == PayoutStatus.TRANSFERRED
                && transferId.equals(payout.getTransferId())) {
            return;
        }

        if (payout.getStatus() != PayoutStatus.PROCESSING) {
            throw new IllegalStateException(
                    "Payout is not processing: " + payoutId
            );
        }

        payout.setTransferId(transferId);
        payout.setStatus(PayoutStatus.TRANSFERRED);

        sellerPayoutRepo.save(payout);
    }

    private long getAvailablePlnBalanceInGrosz() throws StripeException {
        Balance balance = Balance.retrieve();

        return balance.getAvailable()
                .stream()
                .filter(amount -> "pln".equalsIgnoreCase(amount.getCurrency()))
                .mapToLong(Balance.Available::getAmount)
                .sum();
    }
}
