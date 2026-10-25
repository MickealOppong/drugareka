package com.pages.service;

import com.pages.enums.*;
import com.pages.exception.EntityNotFoundException;
import com.pages.model.ListingOrderItem;
import com.pages.model.Payment;
import com.pages.model.PayoutException;
import com.pages.model.SellerPayout;
import com.pages.repository.PaymentRepo;
import com.pages.repository.PayoutExceptionRepo;
import com.pages.repository.SellerPayoutRepo;
import com.stripe.exception.StripeException;
import com.stripe.model.Balance;
import com.stripe.model.Transfer;
import com.stripe.net.RequestOptions;
import com.stripe.param.TransferCreateParams;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
public class PayoutProcessingService {



    private final SellerPayoutRepo sellerPayoutRepo;
    private final PaymentRepo paymentRepo;
    private final PayoutExceptionRepo payoutExceptionRepo;
    private final EmailNotificationService emailNotificationService;

    public PayoutProcessingService(SellerPayoutRepo sellerPayoutRepo, PaymentRepo paymentRepo, PayoutExceptionRepo payoutExceptionRepo, EmailNotificationService emailNotificationService) {
        this.sellerPayoutRepo = sellerPayoutRepo;
        this.paymentRepo = paymentRepo;
        this.payoutExceptionRepo = payoutExceptionRepo;
        this.emailNotificationService = emailNotificationService;
    }


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


    @Transactional
    public void confirmReceiptAndReleaseFunds() {

        List<SellerPayout> approvedPayouts =
                sellerPayoutRepo.findByStatus(PayoutStatus.APPROVED);

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

                BigDecimal sellerPrice = item.getSellerPrice();

                if (sellerPrice == null || sellerPrice.signum() <= 0) {
                    throw new IllegalStateException(
                            "Invalid payout amount for payout " + payoutId
                    );
                }

                long amountInGrosz = sellerPrice
                        .movePointRight(2)
                        .longValueExact();

                long availableBalance = getAvailablePlnBalanceInGrosz();

                if (availableBalance < amountInGrosz) {
                    log.warn(
                            "Insufficient Stripe PLN balance for payout {}. Available: {} grosz, required: {} grosz",
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
                emailNotificationService.sendTransferNotification(email,sellerPrice,orderNumber,transfer.getId());

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
