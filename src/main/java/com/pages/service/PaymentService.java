package com.pages.service;


import com.nimbusds.jose.shaded.gson.JsonElement;
import com.nimbusds.jose.shaded.gson.JsonParser;
import com.pages.dto.*;
import com.pages.enums.*;
import com.pages.exception.EntityNotFoundException;
import com.pages.exception.InvalidOperationException;
import com.pages.exception.InventoryConflictException;
import com.pages.model.*;
import com.pages.repository.*;
import com.pages.service.*;
import com.pages.util.UtilService;
import com.stripe.exception.ApiConnectionException;
import com.stripe.exception.EventDataObjectDeserializationException;
import com.stripe.exception.StripeException;
import com.stripe.model.*;
import com.stripe.model.checkout.Session;
import com.stripe.model.checkout.SessionCollection;
import com.stripe.net.ApiResource;
import com.stripe.net.RequestOptions;
import com.stripe.param.*;
import com.stripe.param.checkout.SessionListParams;
import com.stripe.param.checkout.SessionRetrieveParams;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.parameters.P;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.RollbackOn;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Ref;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService{

    private final PaymentRepo paymentRepo;
    private final ListingOrderRepo listingOrderRepo;
    private final CartService cartService;
    private final SellerPayoutService sellerPayoutService;
    private final InventoryItemRepo inventoryItemRepo;
    private final ShipmentService shipmentService;
    private final SellerProfileService sellerProfileService;
    private final EmailNotificationService emailNotificationService;
    private final ListingOrderItemRepo listingOrderItemRepo;
    private final SellerShipmentTokenService sellerShipmentTokenService;
    private final MessageSource messageSource;
    private final ServiceChargeService serviceChargeService;
    private final OrderDisputeRepo orderDisputeRepo;


    private String getMessage(String code) {
        return messageSource.getMessage(
                code,
                null,
                LocaleContextHolder.getLocale()
        );
    }

    @Transactional
    public ResponseDto<String> createPayment(Session session, ListingOrder listingOrder) {

        try {

            Payment payment = Payment.builder()
                    .listingOrder(listingOrder)
                    .status(PaymentStatus.PENDING)
                    .amount(listingOrder.getOrderTotal())
                    .currency("PLN")
                    .providerSessionId(session.getId())
                    .stripePaymentIntentId(session.getPaymentIntent())
                    .orderNumber(listingOrder.getOrderNumber())
                    .provider(PaymentProvider.STRIPE)
                    .build();

            paymentRepo.save(payment);

            // -------------------------------------------------
            // 6. Return client secret
            // -------------------------------------------------

            return ResponseDto.<String>builder()
                    .data(session.getUrl())
                    .message("Checkout session created")
                    .httpStatus(HttpStatus.OK.value())
                    .build();

        } catch (Exception e) {

            log.error("Failed to create checkout session", e);

            return ResponseDto.<String>builder()
                    .data(null)
                    .message("Unable to create checkout session")
                    .httpStatus(HttpStatus.BAD_REQUEST.value())
                    .build();


        }
    }
    @Transactional
    public ResponseDto<String> createReturnShippingPayment(Session session, OrderDispute orderDispute) {

        try {

            Payment payment = Payment.builder()
                    .status(PaymentStatus.PENDING)
                    .amount(BigDecimal.valueOf(session.getAmountTotal())
                            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP))
                    .currency("PLN")
                    .providerSessionId(session.getId())
                    .stripePaymentIntentId(session.getPaymentIntent())
                    .orderNumber(orderDispute.getOrderNumber())
                    .provider(PaymentProvider.STRIPE)
                    .dispute(orderDispute)
                    .build();

            paymentRepo.save(payment);

            // -------------------------------------------------
            // 6. Return client secret
            // -------------------------------------------------

            return ResponseDto.<String>builder()
                    .data(session.getUrl())
                    .message("Checkout session created")
                    .httpStatus(HttpStatus.OK.value())
                    .build();

        } catch (Exception e) {

            log.error("Failed to create checkout session", e);

            return ResponseDto.<String>builder()
                    .data(null)
                    .message("Unable to create checkout session")
                    .httpStatus(HttpStatus.BAD_REQUEST.value())
                    .build();


        }
    }


    @Transactional
    public ResponseDto<String> createPayUPayment(PayUOrderResponse orderResponse, ListingOrder listingOrder) {

        try {

            Payment payment = Payment.builder()
                    .listingOrder(listingOrder)
                    .status(PaymentStatus.PENDING)
                    .amount(listingOrder.getOrderTotal())
                    .currency("PLN")
                    .orderNumber(orderResponse.getExtOrderId())
                    .providerSessionId(orderResponse.getOrderId())
                    .provider(PaymentProvider.PAYU)
                    .build();

            paymentRepo.save(payment);

            // -------------------------------------------------
            // 6. Return client secret
            // -------------------------------------------------

            return ResponseDto.<String>builder()
                    .data(orderResponse.getRedirectUri())
                    .message("Checkout session created")
                    .httpStatus(HttpStatus.OK.value())
                    .build();

        } catch (Exception e) {

            log.error("Failed to create checkout session", e);

            return ResponseDto.<String>builder()
                    .data(null)
                    .message("Unable to create checkout session")
                    .httpStatus(HttpStatus.BAD_REQUEST.value())
                    .build();


        }
    }

    @Transactional
    public void handlePayUCheckoutCompleted(PayUNotification notification) {


        PayUNotificationOrder notificationOrder = notification.getOrder();

        String orderId = notificationOrder.getOrderId();
        String orderNumber = notificationOrder.getExtOrderId();


        if (orderId == null) {
            throw new IllegalStateException("PayU session does not contain orderId");
        }


        ListingOrder order =
                listingOrderRepo
                        .findByOrderNumber(orderNumber)
                        .orElseThrow(()->new ResponseStatusException(HttpStatus.BAD_REQUEST,"Record doest not exist"));


        Payment payment =
                paymentRepo
                        .findByProviderAndProviderSessionId(PaymentProvider.PAYU,orderId)
                        .orElseThrow(()->new ResponseStatusException(HttpStatus.BAD_REQUEST,"Record doest not exist"));

        /*
         * Idempotency.
         *
         * Stripe can deliver the same webhook more than once.
         */

        if (payment.getStatus() == PaymentStatus.PAID) {
            return;
        }


        payment.setStatus(PaymentStatus.PAID);
        payment.setPaidAt(Instant.now());
        payment.setProviderSessionId(orderId);

        order.setOrderStatus(OrderStatus.PAID);
        order.setPaidAt(Instant.now());

        order.getItems().forEach(orderItem -> {

            InventoryItem inventory = orderItem.getInventoryItem();

            inventory.setStatus(InventoryStatus.SOLD);

            inventory.setReservedUntil(null);

            inventoryItemRepo.save(inventory);

            orderItem.setOrderItemStatus(OrderItemStatus.PAID);
            listingOrderItemRepo.save(orderItem);
        });

        paymentRepo.save(payment);

        ListingOrder listingOrder=  listingOrderRepo.save(order);

        //seller payout;
        sellerPayoutService.createPayouts(order);

        sellerProfileService.updateSellerTotalSales(order.getItems());

        serviceChargeService.createCharge(listingOrder);
        shipmentService.createShipment(order.getItems());
        cartService.deleteCartById(order.getBuyer().getId());

        //BUYER EMAIL NOTIFICATION
        String buyerName = listingOrder.getBuyerNameSnapshot();
        String buyerEmail = listingOrder.getBuyer().getUsername();

        List<EmailProductItemDto> emailDto =listingOrder.getItems().stream().map(item->{

            return EmailProductItemDto.builder()
                    .amount(item.getSellerPrice())
                    .serviceCharge(item.getServiceCharge())
                    .shippingCost(item.getShippingCost())
                    .sellerName(item.getSellerNameSnapshot())
                    .productName(item.getInventoryItem().getProductCatalog().getName())
                    .build();
        }).toList();

        emailNotificationService.sendBulkOrderConfirmationToBuyer(buyerEmail,buyerName,order.getOrderNumber(),emailDto,order.getOrderTotal());

        //SELLER EMAIL NOTIFICATION

        Map<SellerProfile, List<ListingOrderItem> > itemsGroupedBySeller = listingOrderItemRepo.findByListingOrderId(order.getId()).stream()
                .collect(Collectors.groupingBy(ListingOrderItem::getSeller));

        itemsGroupedBySeller.forEach((key, value) -> {

            List<EmailProductItemDto> dto = value.stream().map(item -> {


                return EmailProductItemDto.builder()
                        .productName(item.getInventoryItem().getProductCatalog().getName())
                        .amount(item.getSellerPrice())
                        .shippingCost(item.getShippingCost())
                        .quantity(1L)
                        .build();
            }).toList();
            String sellerEmail = key.getUser().getUsername();
            String sellerName =key.getUser().getUsername();

            SellerShipmentToken token = UtilService.generateShipmentToken();
            sellerShipmentTokenService.createRecord(key,token,listingOrder);

            emailNotificationService.sendBulkOrderActionToSeller(sellerEmail,sellerName, order.getOrderNumber(),dto,token.getToken());
            //update buyer of product awaiting shipment

        });

    }
    @Transactional
    public PaymentStatus getPaymentStatus(String searchKey){

        return paymentRepo.findByOrderNumber(searchKey)

                .or(() -> paymentRepo.findByProviderSessionId(searchKey))
                .map(Payment::getStatus)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        getMessage("api.errors.payment.error")
                ));
    }

    @Transactional
    public void handleNotification(PayUNotification notification)  {

        PayUNotificationOrder order= notification.getOrder();

        Payment payment = paymentRepo
                .findByProviderAndProviderSessionId(
                        PaymentProvider.PAYU,
                        order.getOrderId()
                )
                .orElseThrow(() -> new IllegalStateException(
                        "Payment not found: " + order.getOrderId()
                ));

        switch (order.getStatus()) {

            case "PENDING", "WAITING_FOR_CONFIRMATION" -> {
                payment.setStatus(PaymentStatus.PENDING);
            }

            case "COMPLETED" -> {
                handlePayUCheckoutCompleted(notification);
            }

            case "CANCELED" -> {
                if (payment.getStatus() != PaymentStatus.PAID) {
                    payment.setStatus(PaymentStatus.CANCELLED);
                }
            }

            default -> {
                // Ignore unknown status
            }
        }
    }


    @Transactional
    public void  handleCheckoutCompleted(Event event) throws StripeException {

        // 1. Deserialize the Checkout Session
        Object data= event.getDataObjectDeserializer().deserializeUnsafe();

        Session session = (Session) data;
        String purpose = session.getMetadata() == null
                ? null
                : session.getMetadata().get("payment_purpose");

        if (purpose == null) {
            throw new IllegalStateException(
                    "Missing payment_purpose metadata for session: "
                            + session.getId()
            );
        }

        switch (purpose) {
            case "ORDER" -> finalizePaidCheckoutSession(session);

            case "RETURN_SHIPPING" -> handleReturnShippingPayment(session);

            default -> throw new IllegalStateException(
                    "Unknown payment purpose: " + purpose
            );
        }


    }

    /**
     * return shipping session completed
     */
    @Transactional
   public void  handleReturnShippingPayment(Session session) throws StripeException {
        if (!"paid".equals(session.getPaymentStatus())) {
            log.info("Checkout is not paid. Session: {}", session.getId());
            return;
        }

        Map<String, String> metadata = session.getMetadata();
        String disputeIdStr = metadata != null ? metadata.get("dispute_id") : null;
        if (disputeIdStr == null || disputeIdStr.isBlank()) {
            throw new IllegalStateException("Checkout Session does not contain orderId: " + session.getId());
        }
        Long disputeId = Long.valueOf(disputeIdStr);

        String paymentIntentId = session.getPaymentIntent();
        if (paymentIntentId == null || paymentIntentId.isBlank()) {
            throw new IllegalStateException("Checkout Session has no PaymentIntent: " + session.getId());
        }

        // 1. THIRD-PARTY NETWORK CALL: Performed safely outside database connections
        PaymentIntent intent = PaymentIntent.retrieve(paymentIntentId);
        if (!"succeeded".equals(intent.getStatus())) {
            throw new IllegalStateException("PaymentIntent is not successful: " + intent.getStatus());
        }
        String chargeId = intent.getLatestCharge();

        try {
            // 2. TRANSACTION BOUNDARY: Call the database isolation worker
            Payment payment = paymentRepo.findByProviderSessionId(session.getId()).orElse(null);


            // 3. SECURE IDEMPOTENCY HANDLING: If null is returned, it means a concurrent webhook
            // (like charge.succeeded) already processed this order successfully. Return a clean 200 OK!
            if (payment == null) {
                log.info("Checkout session {} finalization skipped because order #{} was already fulfilled by a concurrent webhook thread.",
                        session.getId(),disputeId);
                return; // EXIT CLEANLY HERE! This returns control back to the controller to send a 200 OK
            }

            payment.setStatus(PaymentStatus.PAID);
            payment.setStripeChargeId(chargeId);
            payment.setStripePaymentIntentId(intent.getId());
            paymentRepo.save(payment);

                      //update dispute to approved by seller for return
            OrderDispute orderDispute = orderDisputeRepo.findByIdForUpdate(disputeId)
                    .orElseThrow(()-> new IllegalStateException("No return initiated"));
            if(orderDispute.getDisputeStatus()==DisputeStatus.AWAITING_RETURN_PAYMENT){
                orderDispute.setDisputeStatus(DisputeStatus.APPROVED);
                orderDisputeRepo.save(orderDispute);
            }
            // 4. POST-COMMIT WORKFLOWS: Execute side-effects ONLY if this specific thread did the database writing
            // 5. Downstream internal data distribution updates
            shipmentService.createReturnShipment(orderDispute);
           // sendNotifications(finalizedOrder);
            log.info("Payment finalized successfully for order {}", payment.getOrderNumber());

        } catch (InventoryConflictException e) {
            log.error("CRITICAL CONFLICT DETECTED: Order #{} was paid, Launching auto-refund.", disputeId);

            RefundCreateParams refundParams = RefundCreateParams.builder()
                    .setCharge(chargeId)
                    .setReason(RefundCreateParams.Reason.DUPLICATE)
                    .build();
            Refund.create(refundParams);

            this.markOrderAsConflictRefundedTx(session.getId(),disputeId);
        }
   }

    /**
     * checkout charge event handler
     * @param event
     */
   @org.springframework.transaction.annotation.Transactional
    public void handleChargeSucceeded(Event event) {
        try {
            Charge charge = (Charge) event.getDataObjectDeserializer().deserializeUnsafe();
            String purpose =charge.getMetadata() == null
                    ? null
                    : charge.getMetadata().get("payment_purpose");

            if (purpose == null) {
                throw new IllegalStateException(
                        "Missing payment_purpose metadata for session: "
                                + charge.getId()
                );
            }

            if(purpose.equals("ORDER")){
                String orderNumber = charge.getTransferGroup();

                if (orderNumber == null || orderNumber.isBlank()) {
                    throw new IllegalStateException("Charge has no transfer group (orderNumber): " + charge.getId());
                }

                Payment payment = paymentRepo.findByOrderNumber(orderNumber)
                        .orElseThrow(() -> new EntityNotFoundException("Payment tracking entry missing for order: " + orderNumber));

                // Idempotency early check: if already processed, return 200 OK instantly!
                if (payment.getStatus() == PaymentStatus.PAID) {
                    log.info("Charge {} event skipped because payment row is already marked as PAID.", charge.getId());
                    return;
                }

                // Pass parameters straight to the database finalizer worker without making an extra Stripe API network call
                ListingOrder finalizedOrder = this.executeFinalizationTx(
                        payment.getProviderSessionId(),
                        payment.getListingOrder().getId(),
                        charge.getPaymentIntent(),
                        charge.getId()
                );

                if (finalizedOrder != null) {
                    shipmentService.createShipment(finalizedOrder.getItems());
                    serviceChargeService.createCharge(finalizedOrder);
                    sellerPayoutService.createPayouts(finalizedOrder);
                    sellerProfileService.updateSellerTotalSales(finalizedOrder.getItems());
                    cartService.deleteCartById(finalizedOrder.getBuyer().getId());
                    sendNotifications(finalizedOrder);
                    log.info("Payment finalized successfully via charge.succeeded wrapper for order #{}", finalizedOrder.getOrderNumber());
                }
            }
            if(purpose.equals("RETURN_SHIPPING")){
                Long disputeId = Long.valueOf(charge.getMetadata().get("dispute_id"));
                Payment payment = paymentRepo.findByDisputeIdForUpdate(disputeId).orElse(null);
                if(payment!=null && payment.getStatus()!=PaymentStatus.PAID){
                    payment.setStatus(PaymentStatus.PAID);
                    paymentRepo.save(payment);
                }

                //update dispute to approved by seller for return
                OrderDispute orderDispute = orderDisputeRepo.findByIdForUpdate(disputeId)
                        .orElseThrow(()-> new IllegalStateException("No return initiated is not successful: "));

                if(orderDispute.getDisputeStatus()==DisputeStatus.AWAITING_RETURN_PAYMENT){
                    orderDispute.setDisputeStatus(DisputeStatus.APPROVED);
                    orderDisputeRepo.save(orderDispute);

                    //create shipment
                    shipmentService.createReturnShipment(orderDispute);
                }

            }


        } catch (Exception e) {
            log.error("Stripe webhook processing failed for charge.succeeded: {}", e.getMessage(), e);
            throw new IllegalStateException("Failed to process charge.succeeded event " + event.getId(), e);
        }
    }


    private void sendNotifications(ListingOrder listingOrder){
        String buyerName = listingOrder.getBuyerNameSnapshot();
        String buyerEmail = listingOrder.getBuyer().getUsername();

        List<EmailProductItemDto> buyerEmailItems =
                listingOrder.getItems().stream()
                        .map(item -> EmailProductItemDto.builder()
                                .amount(item.getSellerPrice())
                                .shippingCost(item.getShippingCost())
                                .serviceCharge(item.getServiceCharge())
                                .quantity(1L)
                                .sellerName(item.getSellerNameSnapshot())
                                .productName(
                                        item.getInventoryItem()
                                                .getProductCatalog()
                                                .getName()
                                )
                                .build())
                        .toList();

        emailNotificationService.sendBulkOrderConfirmationToBuyer(
                buyerEmail,
                buyerName,
                listingOrder.getOrderNumber(),
                buyerEmailItems,
                listingOrder.getOrderTotal()
        );

        // 13. Group order items by seller
        Map<SellerProfile, List<ListingOrderItem>> itemsGroupedBySeller =
                listingOrderItemRepo.findByListingOrderId(listingOrder.getId())
                        .stream()
                        .collect(Collectors.groupingBy(
                                ListingOrderItem::getSeller
                        ));

        // 14. Send seller notifications and shipment tokens
        itemsGroupedBySeller.forEach((seller, items) -> {

            List<EmailProductItemDto> sellerEmailItems =
                    items.stream()
                            .map(item -> EmailProductItemDto.builder()
                                    .productName(
                                            item.getInventoryItem()
                                                    .getProductCatalog()
                                                    .getName()
                                    )
                                    .amount(item.getSellerPrice())
                                    .shippingCost(item.getShippingCost())
                                    .serviceCharge(item.getServiceCharge())
                                    .quantity(1L)
                                    .build())
                            .toList();

            String sellerEmail = seller.getUser().getUsername();
            String sellerName = seller.getUser().getUsername();

            SellerShipmentToken token =
                    UtilService.generateShipmentToken();

            sellerShipmentTokenService.createRecord(
                    seller,
                    token,
                    listingOrder
            );

            emailNotificationService.sendBulkOrderActionToSeller(
                    sellerEmail,
                    sellerName,
                    listingOrder.getOrderNumber(),
                    sellerEmailItems,
                    token.getToken()
            );
        });

    }

    @Transactional
    public void   createRefund(ListingOrderItem order) {

        List<Payment> payments=paymentRepo.findByListingOrderId(order.getListingOrder().getId());

        double netTotal = payments.stream()
                .filter(record -> record.getStatus().equals(PaymentStatus.PAID)
                        || record.getStatus().equals(PaymentStatus.AWAITING_REFUND))
                .mapToDouble(record->record.getAmount().doubleValue()).sum();

        if (netTotal <= 0.01) {
            log.warn("Double-Refund Prevention: Order has already been fully refunded (Net Balance: {} PLN).", netTotal);
            throw new InvalidOperationException("Cannot refund below zero.");
        }
        Payment originalPayment = paymentRepo.findByListingOrderIdAndStatus(order.getListingOrder().getId(),PaymentStatus.PAID)
                .orElse(null);

        if(originalPayment==null){
            throw new EntityNotFoundException("Payment record does not exist for refund");
        }
        try {

            Payment refund = Payment.builder()
                    .listingOrder(order.getListingOrder())
                    .status(PaymentStatus.AWAITING_REFUND)
                    .amount(order.getSellerPrice().negate())
                    .provider(originalPayment.getProvider())
                    .currency(originalPayment.getCurrency())
                    .providerSessionId(originalPayment.getProviderSessionId())
                    .stripePaymentIntentId(originalPayment.getStripePaymentIntentId())
                    .build();

            paymentRepo.save(refund);

        } catch (Exception e) {

            log.error("Failed to create refund", e);
            throw new InvalidOperationException("Failed to create refund");
        }
    }


    @Transactional
    public void handleRefundCreated(Event event) {
        handleIndividualRefundEvent(event, "refund.created");
    }

    @Transactional
    public void handleRefundUpdated(Event event) {
        handleIndividualRefundEvent(event, "refund.updated");
    }

    @Transactional
    private void handleIndividualRefundEvent(Event event, String eventType) {

        log.info("Processing Stripe event: id={}, type={}",
                event.getId(), eventType);
        try {
            var deserializer = event.getDataObjectDeserializer();
            Refund refund;

            if (deserializer.getObject().isPresent()
                    && deserializer.getObject().get() instanceof Refund parsedRefund) {

                refund = parsedRefund;

            } else {
                refund = (Refund) event.getDataObjectDeserializer().deserializeUnsafe();
            }

            if (refund == null || refund.getId() == null) {
                throw new IllegalArgumentException(
                        "Stripe Refund payload is missing its ID."
                );
            }

            log.info("Stripe Refund: id={}, status={}, amount={}, paymentIntent={}",
                    refund.getId(),
                    refund.getStatus(),
                    refund.getAmount(),
                    refund.getPaymentIntent());

            // A refund can be pending when refund.created is received.
            if (!"succeeded".equalsIgnoreCase(refund.getStatus())) {
                log.info("Refund {} is not successful yet. Current status={}",
                        refund.getId(), refund.getStatus());
                return;
            }

            Map<String, String> metadata = refund.getMetadata();
            String ledgerId = metadata == null
                    ? null
                    : metadata.get("payment_ledger_id");


            Payment ledgerOptional=null;

            if (ledgerId != null && !ledgerId.isBlank()) {
                ledgerOptional = paymentRepo.findById(Long.valueOf(ledgerId)).orElse(null);
            }

            if (ledgerOptional==null) {
                log.error(
                        "No refund ledger found for Stripe Refund ID {}. Metadata ledger ID={}",
                        refund.getId(), ledgerId
                );

                throw new IllegalStateException(
                        "Refund ledger not found for Stripe Refund ID " + refund.getId()
                );
            }

            Payment ledger = ledgerOptional;

            // Protect against duplicate webhook delivery.
            if (ledger.getStatus() == PaymentStatus.REFUNDED) {
                log.info("Refund ledger {} is already REFUNDED.", ledger.getId());
                return;
            }

            // Persist the Stripe Refund ID for future reconciliation.
            ledger.setStripeRefundId(refund.getId());
            ledger.setStatus(PaymentStatus.REFUNDED);

            paymentRepo.save(ledger);

            log.info(
                    "Refund ledger {} marked REFUNDED. Stripe Refund ID={}, amount={} minor units.",
                    ledger.getId(), refund.getId(), refund.getAmount()
            );

        } catch (NumberFormatException e) {
            log.error("Invalid payment_ledger_id in Stripe Refund metadata. Event ID={}",
                    event.getId(), e);

            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Invalid refund ledger metadata",
                    e
            );

        } catch (Exception e) {
            log.error("Failed to process Stripe {} event. Event ID={}",
                    eventType, event.getId(), e);

            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Stripe refund event processing failed",
                    e
            );
        }


    }

    @Transactional
    public void handleChargeRefunded(Event event) {

        log.info("Processing Stripe charge.refunded: eventId={}", event.getId());

        try {
            var deserializer = event.getDataObjectDeserializer();
            Charge charge;

            if (deserializer.getObject().isPresent()
                    && deserializer.getObject().get() instanceof Charge parsedCharge) {

                charge = parsedCharge;

            } else {
                charge = (Charge) event.getDataObjectDeserializer().deserializeUnsafe();
            }

            if (charge == null || charge.getId() == null) {
                throw new IllegalArgumentException(
                        "Stripe Charge payload is missing its ID."
                );
            }

            long originalAmount = charge.getAmount() == null
                    ? 0L : charge.getAmount();

            long totalRefunded = charge.getAmountRefunded() == null
                    ? 0L : charge.getAmountRefunded();

            boolean fullyRefunded =
                    Boolean.TRUE.equals(charge.getRefunded())
                            || (originalAmount > 0 && totalRefunded >= originalAmount);

            long remainingAmount = Math.max(0L, originalAmount - totalRefunded);

            log.info(
                    "Stripe refund reconciliation: chargeId={}, paymentIntent={}, " +
                            "originalAmount={}, totalRefunded={}, remainingAmount={}, fullyRefunded={}",
                    charge.getId(),
                    charge.getPaymentIntent(),
                    originalAmount,
                    totalRefunded,
                    remainingAmount,
                    fullyRefunded
            );

            // Reconciliation only.
            // Do not modify the original Payment status.
            // Individual refund ledger records are updated by refund.created/refund.updated.

        } catch (Exception e) {
            log.error(
                    "Stripe charge.refunded reconciliation failed. Event ID={}",
                    event.getId(),
                    e
            );

            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Stripe refund reconciliation failed",
                    e
            );
        }
    }

    @Transactional
    public void createTotalRefund(ListingOrder order) {

        if (order == null) {
            return;
        }

        List<Payment> payments = paymentRepo.findByListingOrderId(order.getId());

        // Calculate current net balance in your database
        BigDecimal netTotal = payments.stream()
                .filter(record -> record.getStatus().equals(PaymentStatus.PAID)
                        || record.getStatus().equals(PaymentStatus.AWAITING_REFUND))
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (netTotal.compareTo(new BigDecimal("0.01")) <= 0) {
            log.warn("Double-Refund Prevention: Order {} already fully refunded (Balance: {} PLN).", order.getId(), netTotal);
            throw new InvalidOperationException("Cannot refund below zero.");
        }

        Payment originalPayment = paymentRepo.findByListingOrderIdAndStatus(order.getId(), PaymentStatus.PAID)
                .orElseThrow(() -> new EntityNotFoundException("Payment record does not exist for refund"));

        try {
            // 1. Create a local accounting record matching the exact remaining net balance
            Payment refundRecord = Payment.builder()
                    .listingOrder(order)
                    .status(PaymentStatus.AWAITING_REFUND)
                    .amount(netTotal.negate()) // Refund exactly what's left
                    .provider(originalPayment.getProvider())
                    .currency(originalPayment.getCurrency())
                    .orderNumber(originalPayment.getOrderNumber())
                    .providerSessionId(originalPayment.getProviderSessionId())
                    .stripePaymentIntentId(originalPayment.getStripePaymentIntentId())
                    .build();

            paymentRepo.save(refundRecord);

        } catch (Exception e) {
            log.error("Failed to create refund for order {}", order.getId(), e);
            throw new InvalidOperationException("Failed to complete refund processing.");
        }
    }

    private long getRemainingRefundableAmount(String chargeId)
            throws StripeException {

        Charge charge = Charge.retrieve(chargeId);

        long chargeAmount = charge.getAmount();
        long amountRefunded = charge.getAmountRefunded();

        return chargeAmount - amountRefunded;
    }

    @Transactional
    public void reconcileRefundToStripeLedge(){
        // Fetch rows using FOR UPDATE to prevent multiple nodes from selecting the same queue items
        List<Payment> payments = paymentRepo.findByStatusAndProviderAndCreatedAtBeforeForUpdate(
                PaymentStatus.AWAITING_REFUND,
                PaymentProvider.STRIPE,
                Instant.now()
        );

        for (Payment refund : payments) {
            try {
                Payment originalPayment = paymentRepo.findByListingOrderIdAndStatus(refund.getListingOrder().getId(),PaymentStatus.PAID).orElse(null);

                // Execute each individual refund inside its own isolated database transaction

                if(originalPayment!=null){
                    reconcileRefundsForLedger(originalPayment.getStripeChargeId());
                }
            } catch (Exception e) {
                // Capture the error safely. The loop will advance to the next payment row cleanly.
                log.error("Queue Processor: Skipping payment id {} due to processing failure.", refund.getId(), e);
            }
        }

    }

    @Transactional
    private void reconcileRefundsForLedger(String chargeId)
            throws StripeException {

        RefundListParams params = RefundListParams.builder()
                .setCharge(chargeId)
                .setLimit(100L)
                .build();

        RefundCollection result = Refund.list(params);

        while (true) {
            for (Refund stripeRefund : result.getData()) {

                String ledgerId = stripeRefund.getMetadata() == null
                        ? null
                        : stripeRefund.getMetadata()
                        .get("payment_ledger_id");

                if (ledgerId == null) {
                    continue;
                }

               paymentRepo.findByIdForUpdate(Long.valueOf(ledgerId))
                        .ifPresent(ledger -> {
                            if ("succeeded".equals(stripeRefund.getStatus()) && !ledger.getStatus().equals(PaymentStatus.REFUNDED)) {
                                // Update the associated refund record,
                                ledger.setStatus(PaymentStatus.REFUNDED);
                                ledger.setStripeRefundId(stripeRefund.getId());
                                paymentRepo.save(ledger);
                            }
                        });
            }

            if (!Boolean.TRUE.equals(result.getHasMore())
                    || result.getData().isEmpty()) {
                break;
            }

            String lastRefundId = result.getData()
                    .get(result.getData().size() - 1)
                    .getId();

            params = RefundListParams.builder()
                    .setCharge(chargeId)
                    .setLimit(100L)
                    .setStartingAfter(lastRefundId)
                    .build();

            result = Refund.list(params);
        }
    }

    public void processSingleIsolatedRefund(Payment refund)
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
                    .setReason(RefundCreateParams.Reason.FRAUDULENT)
                    .setAmount(amountInGrosze)
                    .setMetadata(metadata)
                    .putMetadata("payment_ledger_id", String.valueOf(refund.getId()))
                    .build();

            Refund stripeRefund = Refund.create(refundParams);

            log.info(
                    "Stripe refund created: {} grosze, Payment ID: {}, Stripe Refund ID: {}",
                    amountInGrosze,
                    refund.getId(),
                    stripeRefund.getId()
            );



        } catch (StripeException e) {
            log.error(
                    "Stripe refund failed for Payment ID: {}",
                    refund.getId(),
                    e
            );

        }
    }

    @Transactional
    public void executeStripeRefund() {

        // Fetch rows using FOR UPDATE to prevent multiple nodes from selecting the same queue items
        List<Payment> payments = paymentRepo.findByStatusAndProviderAndCreatedAtBeforeForUpdate(
                PaymentStatus.AWAITING_REFUND,
                PaymentProvider.STRIPE,
                Instant.now()
        );

        for (Payment refund : payments) {


            try {

                Payment originalPayment = paymentRepo.findByListingOrderIdAndStatus(refund.getListingOrder().getId(),PaymentStatus.PAID).orElse(null);
                long remaining =0L;
                if(originalPayment!=null){
                   remaining = getRemainingRefundableAmount(originalPayment.getStripeChargeId());
                   log.info("Balance {}",remaining);
                }

                long requested = refund.getAmount().negate()
                        .movePointRight(2)
                        .longValueExact();

                if (requested <= 0 || requested > remaining) {
                   log.info("Refund amount :{} exceeds the remaining refundable amount. performing reconciliation to Stripe balance for :{}",requested,refund.getId());
                    reconcileRefundToStripeLedge();
                    continue;
                }
                // Execute each individual refund inside its own isolated database transaction
                processSingleIsolatedRefund(refund);
            } catch (Exception e) {
                // Capture the error safely. The loop will advance to the next payment row cleanly.
                log.error("Queue Processor: Skipping payment id {} due to processing failure.", refund.getId(), e);
            }
        }
        }






    public void finalizePaidCheckoutSession(Session session) throws StripeException {
        if (!"paid".equals(session.getPaymentStatus())) {
            log.info("Checkout is not paid. Session: {}", session.getId());
            return;
        }

        Map<String, String> metadata = session.getMetadata();
        String orderIdStr = metadata != null ? metadata.get("order_id") : null;
        if (orderIdStr == null || orderIdStr.isBlank()) {
            throw new IllegalStateException("Checkout Session does not contain orderId: " + session.getId());
        }
        Long orderId = Long.valueOf(orderIdStr);

        String paymentIntentId = session.getPaymentIntent();
        if (paymentIntentId == null || paymentIntentId.isBlank()) {
            throw new IllegalStateException("Checkout Session has no PaymentIntent: " + session.getId());
        }

        // 1. THIRD-PARTY NETWORK CALL: Performed safely outside database connections
        PaymentIntent intent = PaymentIntent.retrieve(paymentIntentId);
        if (!"succeeded".equals(intent.getStatus())) {
            throw new IllegalStateException("PaymentIntent is not successful: " + intent.getStatus());
        }
        String chargeId = intent.getLatestCharge();

        try {
            // 2. TRANSACTION BOUNDARY: Call the database isolation worker
            ListingOrder finalizedOrder = this.executeFinalizationTx(session.getId(), orderId, paymentIntentId, chargeId);

            // 3. SECURE IDEMPOTENCY HANDLING: If null is returned, it means a concurrent webhook
            // (like charge.succeeded) already processed this order successfully. Return a clean 200 OK!
            if (finalizedOrder == null) {
                log.info("Checkout session {} finalization skipped because order #{} was already fulfilled by a concurrent webhook thread.",
                        session.getId(), orderId);
                return; // EXIT CLEANLY HERE! This returns control back to the controller to send a 200 OK
            }

            // 4. POST-COMMIT WORKFLOWS: Execute side-effects ONLY if this specific thread did the database writing
            // 5. Downstream internal data distribution updates
            shipmentService.createShipment(finalizedOrder.getItems());
            serviceChargeService.createCharge(finalizedOrder);
            sellerPayoutService.createPayouts(finalizedOrder);
            sellerProfileService.updateSellerTotalSales(finalizedOrder.getItems());
            cartService.deleteCartById(finalizedOrder.getBuyer().getId());
            sendNotifications(finalizedOrder);
            log.info("Payment finalized successfully for order {}", finalizedOrder.getOrderNumber());

        } catch (InventoryConflictException e) {
            log.error("CRITICAL CONFLICT DETECTED: Order #{} was paid, but items are already SOLD. Launching auto-refund.", orderId);

            RefundCreateParams refundParams = RefundCreateParams.builder()
                    .setCharge(chargeId)
                    .setReason(RefundCreateParams.Reason.DUPLICATE)
                    .build();
            Refund.create(refundParams);

            this.markOrderAsConflictRefundedTx(session.getId(), orderId);
        }
    }


    protected ListingOrder executeFinalizationTx(String sessionId, Long orderId, String paymentIntentId, String chargeId) {

        // 1. Lock and validate Payment row to prevent concurrent webhook execution collisions
        Payment payment = paymentRepo.findByProviderSessionIdForUpdate(sessionId)
                .orElseThrow(() -> new IllegalStateException("Payment not found for session: " + sessionId));

        if (payment.getStatus() == PaymentStatus.PAID) {
            log.info("Payment already finalized for session {}", sessionId);
            return null;
        }

        // 2. Lock and validate Order row
        ListingOrder order = listingOrderRepo.findByIdForUpdate(orderId)
                .orElseThrow(() -> new IllegalStateException("Order not found: " + orderId));

        if (order.getOrderStatus() == OrderStatus.PAID) {
            log.info("Order {} is already paid", orderId);
            return null;
        }

        // 3. CRITICAL: Validate that the physical inventory items haven't been sold yet
        for (ListingOrderItem item : order.getItems()) {
            InventoryItem inventory = item.getInventoryItem();

            // Fetch and lock the raw inventory row directly from the database to ensure we aren't reading dirty cache data
            InventoryItem lockedInventory = inventoryItemRepo.findByIdForUpdate(inventory.getId())
                    .orElseThrow(() -> new IllegalStateException("Inventory item missing: " + inventory.getId()));

            if (lockedInventory.getStatus() == InventoryStatus.SOLD) {
                // Throw a custom runtime exception to explicitly force a database rollback
                throw new InventoryConflictException("Item " + lockedInventory.getId() + " is already SOLD. Forcing transaction rollback.");
            }

            // Update locked item fields securely
            lockedInventory.setStatus(InventoryStatus.SOLD);
            lockedInventory.setReservedUntil(null);
            lockedInventory.setReservedBy(null);
            inventoryItemRepo.save(lockedInventory);

            item.setOrderItemStatus(OrderItemStatus.PAID);
            listingOrderItemRepo.save(item);
        }

        // 4. Update core order transaction headers
        Instant paidAt = Instant.now();
        payment.setStatus(PaymentStatus.PAID);
        payment.setPaidAt(paidAt);
        payment.setStripePaymentIntentId(paymentIntentId);
        payment.setStripeChargeId(chargeId);
        paymentRepo.save(payment);

        order.setOrderStatus(OrderStatus.PAID);
        order.setPaidAt(paidAt);

        return listingOrderRepo.save(order);
    }


    protected void markOrderAsConflictRefundedTx(String sessionId, Long orderId) {
        paymentRepo.findByProviderSessionIdForUpdate(sessionId).ifPresent(payment -> {
            payment.setStatus(PaymentStatus.FAILED);
            paymentRepo.save(payment);
        });

        listingOrderRepo.findByIdForUpdate(orderId).ifPresent(order -> {
            order.setOrderStatus(OrderStatus.CANCELLED);
            listingOrderRepo.save(order);
        });
    }


    private static InventoryItem getInventoryItem(ListingOrderItem item) {
        InventoryItem inventory = item.getInventoryItem();

        // Do not silently overwrite inventory sold to another buyer.
        if (inventory.getStatus() == InventoryStatus.SOLD) {
            throw new IllegalStateException(
                    "Inventory conflict: item " + item.getId()
                            + " is already sold"
            );
        }

        inventory.setStatus(InventoryStatus.SOLD);
        inventory.setReservedUntil(null);
        return inventory;
    }



    private void finalizePaidCheckoutReconciliation(Session session) {
        try {

            if (!"paid".equals(session.getPaymentStatus())) {
                throw new IllegalStateException(
                        "Cannot finalize unpaid Checkout Session: " + session.getId()
                );
            }

            Map<String, String> metadata = session.getMetadata();
            String orderId = metadata == null ? null : metadata.get("order_id");

            if (orderId == null || orderId.isBlank()) {
                throw new IllegalStateException(
                        "Checkout Session does not contain orderId: " + session.getId()
                );
            }

            Payment payment = paymentRepo
                    .findByProviderSessionIdForUpdate(session.getId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Payment not found for session: " + session.getId()
                    ));

            if (payment.getStatus() == PaymentStatus.PAID) {
                log.info("Payment already finalized for session {}", session.getId());
                return;
            }

            ListingOrder order = listingOrderRepo
                    .findByIdForUpdate(Long.valueOf(orderId))
                    .orElseThrow(() -> new IllegalStateException(
                            "Order not found: " + orderId
                    ));

            String paymentIntentId = session.getPaymentIntent();

            if (paymentIntentId == null || paymentIntentId.isBlank()) {
                throw new IllegalStateException(
                        "Checkout Session has no PaymentIntent: " + session.getId()
                );
            }

            if (!payment.getListingOrder().getId().equals(order.getId())) {
                throw new IllegalStateException(
                        "Stripe session, payment and order do not match"
                );
            }

            // Verify the actual PaymentIntent state before fulfilling.
            PaymentIntent intent = PaymentIntent.retrieve(paymentIntentId);

            if (!"succeeded".equals(intent.getStatus())) {
                throw new IllegalStateException(
                        "PaymentIntent is not successful: " + intent.getStatus()
                );
            }

            Instant paidAt = Instant.now();

            payment.setStatus(PaymentStatus.PAID);
            payment.setPaidAt(paidAt);
            payment.setStripePaymentIntentId(paymentIntentId);
            payment.setStripeChargeId(intent.getLatestCharge());
            order.setOrderStatus(OrderStatus.PAID);
            order.setPaidAt(paidAt);

            for (ListingOrderItem item : order.getItems()) {
                InventoryItem inventory = item.getInventoryItem();

                inventory.setStatus(InventoryStatus.SOLD);
                inventory.setReservedUntil(null);
                inventory.setReservedBy(null);
                inventoryItemRepo.save(inventory);

                item.setOrderItemStatus(OrderItemStatus.PAID);
                listingOrderItemRepo.save(item);
            }

            paymentRepo.save(payment);
            // These operations must themselves be idempotent.
            serviceChargeService.createCharge(order);
            sellerPayoutService.createPayouts(order);
            sellerProfileService.updateSellerTotalSales(order.getItems());
            shipmentService.createShipment(order.getItems());
            cartService.deleteCartById(order.getBuyer().getId());

            sendNotifications(order);
            log.info(
                    "Payment finalized successfully for order {}",
                    order.getOrderNumber()
            );
        }catch (StripeException e){
            log.error(
                    "Stripe error:{}",
                    e.getMessage());
        }catch (Exception e){
            log.error(
                    "General error:{}",
                    e.getMessage());
        }
    }

    @Transactional
    public void handleDelayedPaymentIntentSuccess(Event event) throws StripeException {
        Object stripeObject = event.getDataObjectDeserializer().deserializeUnsafe();
        if (!(stripeObject instanceof PaymentIntent paymentIntent)) {
            log.warn("Unexpected payload format in payment_intent.succeeded event. Skipping.");
            return;
        }

        String purpose = paymentIntent.getMetadata() == null
                ? null
                : paymentIntent.getMetadata().get("payment_purpose");

        if (purpose == null) {
            throw new IllegalStateException(
                    "Missing payment_purpose metadata for session: "
                            + paymentIntent.getId()
            );
        }


        if(purpose.equals("ORDER")){
            String paymentIntentId = paymentIntent.getId();
            String trueChargeId = paymentIntent.getLatestCharge();
            String transferGroup = paymentIntent.getTransferGroup();

            // Trace the target entry using database records natively
            Payment payment = paymentRepo.findByOrderNumber(transferGroup)
                    .orElse(null);

            if (payment == null) {
                log.warn("Payment tracking record missing for intent: {}. Skipping cleanly.", paymentIntentId);
                return;
            }

            // Idempotency early check: if already processed, return 200 OK instantly!
            if (payment.getStatus() == PaymentStatus.PAID) {
                log.info("Payment intent {} already marked as PAID. Skipping.", paymentIntentId);
                return;
            }

            ListingOrder order = payment.getListingOrder();
            if (order.getOrderStatus() == OrderStatus.CANCELLED) {
                // Recovery logic for late payments
                ListingOrder restoredOrder = this.executeDelayedPaymentSuccessFulfillment(order.getId(), trueChargeId);
                if (restoredOrder != null) {
                    serviceChargeService.createCharge(restoredOrder);
                    sellerPayoutService.createPayouts(restoredOrder);
                    sellerProfileService.updateSellerTotalSales(restoredOrder.getItems());
                    shipmentService.createShipment(restoredOrder.getItems());
                    cartService.deleteCartById(restoredOrder.getBuyer().getId());
                    sendNotifications(restoredOrder);
                } else {
                    RefundCreateParams refundParams = RefundCreateParams.builder()
                            .setCharge(trueChargeId)
                            .setReason(RefundCreateParams.Reason.DUPLICATE)
                            .build();
                    Refund.create(refundParams);
                    this.executeCheckoutCancellationTx(payment.getProviderSessionId(), order.getId());
                }
                return;
            }

            if (order.getOrderStatus() == OrderStatus.PROCESSING) {
                // Execute database updates directly using parameters provided natively by the webhook payload
                ListingOrder finalizedOrder = this.executeFinalizationTx(payment.getProviderSessionId(), order.getId(), paymentIntentId, trueChargeId);
                if (finalizedOrder != null) {
                    serviceChargeService.createCharge(finalizedOrder);
                    sellerPayoutService.createPayouts(finalizedOrder);
                    sellerProfileService.updateSellerTotalSales(finalizedOrder.getItems());
                    shipmentService.createShipment(finalizedOrder.getItems());
                    cartService.deleteCartById(finalizedOrder.getBuyer().getId());
                    sendNotifications(finalizedOrder);
                }
            }
        }
        if(purpose.equals("RETURN_SHIPPING")){
          Long disputeId= Long.valueOf(paymentIntent.getMetadata().get("dispute_id"));
         Payment payment=   paymentRepo.findByDisputeIdForUpdate(disputeId)
                 .orElseThrow(()->new IllegalStateException("No payment initiated: "));

         Charge charge =(Charge) paymentIntent.getLatestChargeObject();
         if(payment!=null && !payment.getStatus().equals(PaymentStatus.PAID)){
             payment.setStatus(PaymentStatus.PAID);
             payment.setStripePaymentIntentId(paymentIntent.getId());
            payment.setStripeChargeId(charge.getId());
             paymentRepo.save(payment);
             //create shipment
         }


            //update dispute to approved by seller for return
            OrderDispute orderDispute = orderDisputeRepo.findByIdForUpdate(disputeId)
                    .orElseThrow(()-> new IllegalStateException("No return initiated : "));

            if( orderDispute.getDisputeStatus()==DisputeStatus.AWAITING_RETURN_PAYMENT){
                orderDispute.setDisputeStatus(DisputeStatus.APPROVED);
                orderDisputeRepo.save(orderDispute);


                //create shipment
                shipmentService.createReturnShipment(orderDispute);
            }


        } else {
            log.warn("Unknown payment purpose for session {}", paymentIntent.getId());
        }


    }


    protected ListingOrder executeDelayedPaymentSuccessFulfillment(Long orderId, String chargeId) {
        // 1. Lock the order row exclusively to prevent concurrent modification checks
        ListingOrder order = listingOrderRepo.findByIdForUpdate(orderId)
                .orElseThrow(() -> new IllegalStateException("Order missing for execution: " + orderId));

        // 2. Fetch the corresponding payment tracking row and lock it
        Payment payment = paymentRepo.findByListingOrderId(orderId).stream()
                .filter(p -> p.getProvider() == PaymentProvider.STRIPE)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Payment tracking record missing for order: " + orderId));

        // Re-lock the payment row explicitly to keep locking sequence uniform
        payment = paymentRepo.findById(payment.getId()).orElse(payment);

        // 3. Double-Sale Prevention Validation Check
        for (ListingOrderItem item : order.getItems()) {
            InventoryItem inventory = item.getInventoryItem();

            // Lock the specific physical stock catalog row to ensure we read absolute fresh state
            InventoryItem lockedInventory = inventoryItemRepo.findByIdForUpdate(inventory.getId())
                    .orElseThrow(() -> new IllegalStateException("Inventory item missing: " + inventory.getId()));

            // CRITICAL CONFLICT: The item reservation expired, and someone else bought it in the meantime!
            if (lockedInventory.getStatus() == InventoryStatus.SOLD) {
                log.warn("Inventory item {} is already SOLD to another buyer. Recovery fulfillment impossible.", lockedInventory.getId());
                return null; // Return null to signal to the caller that an auto-refund must be executed
            }
        }

        // PATH 4: Inventory is still safely available! Reverse the cancellation and fulfill the order
        log.info("Inventory is still open. Reversing cancellation state for order #{}", order.getOrderNumber());

        Instant paidAt = Instant.now();

        // Update core payment parameters
        payment.setStatus(PaymentStatus.PAID);
        payment.setPaidAt(paidAt);
        payment.setStripeChargeId(chargeId);
        paymentRepo.save(payment);

        // Update order header statuses
        order.setOrderStatus(OrderStatus.PAID);
        order.setPaidAt(paidAt);

        // Update stock item tables safely to lock down the items permanently
        for (ListingOrderItem item : order.getItems()) {
            InventoryItem lockedInventory = inventoryItemRepo.findByIdForUpdate(item.getInventoryItem().getId()).get();

            lockedInventory.setStatus(InventoryStatus.SOLD);
            lockedInventory.setReservedBy(null);
            lockedInventory.setReservedUntil(null);
            inventoryItemRepo.save(lockedInventory);

            item.setOrderItemStatus(OrderItemStatus.PAID);
            listingOrderItemRepo.save(item);
        }

        ListingOrder savedOrder = listingOrderRepo.save(order);

        // 5. Execute downstream core internal processes safely inside the transaction scope
        serviceChargeService.createCharge(savedOrder);
        sellerPayoutService.createPayouts(savedOrder);
        sellerProfileService.updateSellerTotalSales(savedOrder.getItems());
        cartService.deleteCartById(savedOrder.getBuyer().getId());

        return savedOrder;
    }




    // REMOVED @Transactional to protect the database connection pool from Stripe API latency drops
    public void handleAsyncPaymentFailed(Event event) throws StripeException {
        String paymentIntentId = null;
        String sessionId = null;
        Map<String, String> metadata = null;
        String purpose = null;
        Object stripeObject = event.getDataObjectDeserializer().deserializeUnsafe();

        // 1. Polymorphic Extraction: Handle either Session or PaymentIntent event payloads
        if (stripeObject instanceof Session eventSession) {
            Session session = Session.retrieve(eventSession.getId());
            sessionId = session.getId();
            paymentIntentId = session.getPaymentIntent();
            metadata = session.getMetadata();
            purpose=eventSession.getMetadata() == null
                    ? null
                    : eventSession.getMetadata().get("payment_purpose");
        } else if (stripeObject instanceof PaymentIntent eventIntent) {
            PaymentIntent intent = PaymentIntent.retrieve(eventIntent.getId());
            paymentIntentId = intent.getId();
            metadata = intent.getMetadata();

            purpose=eventIntent.getMetadata() == null
                    ? null
                    : eventIntent.getMetadata().get("payment_purpose");
        } else {
            throw new IllegalStateException("Unexpected object type inside failure event: " + stripeObject.getClass().getName());
        }

        if (paymentIntentId == null || paymentIntentId.isBlank()) {
            throw new IllegalStateException("Failed event object contains no reference to a PaymentIntent");
        }



        if (purpose == null) {
            throw new IllegalStateException(
                    "Missing payment_purpose metadata for session: "
                            + paymentIntentId
            );
        }

        if(purpose.equals("ORDER")){
            String orderIdStr = metadata != null ? metadata.get("order_id") : null;
            if (orderIdStr == null || orderIdStr.isBlank()) {
                throw new IllegalStateException("Failed event payload metadata does not contain an orderId");
            }
            Long orderId = Long.valueOf(orderIdStr);

            // 2. Retrieve definitive state from Stripe API out-of-transaction bounds
            PaymentIntent intent = PaymentIntent.retrieve(paymentIntentId);
            String intentStatus = intent.getStatus();

            // Guard Clause A: If it actually succeeded, run successful finalization paths
            if ("succeeded".equals(intentStatus)) {
                log.warn("Async failure event conflicts with successful status for PaymentIntent {}. Reconciling order {}.", paymentIntentId, orderId);
                if (stripeObject instanceof Session session) {
                    finalizePaidCheckoutSession(session);
                } else {
                    // Reconcile raw PaymentIntent via its ID reference using your existing method
                    handleCheckoutCompleted(event);
                }
                return;
            }

            // Guard Clause B: Do not release inventory while the payment is actively processing
            if ("processing".equals(intentStatus)) {
                log.info("PaymentIntent {} is still processing. Keeping inventory reserved for order {}.", paymentIntentId, orderId);
                return;
            }

            // Guard Clause C: Only proceed with eviction if status confirms definitive failure
            if (!"canceled".equals(intentStatus) && !"requires_payment_method".equals(intentStatus)) {
                log.warn("PaymentIntent {} has an unexpected status: {}. Leaving order {} unchanged.", paymentIntentId, intentStatus, orderId);
                return;
            }

            // 3. Delegate to the isolated transactional method for safe database mutations
            this.executeCheckoutCancellationTx(sessionId, orderId);
        }
        if(purpose.equals("RETURN_SHIPPING")){
            String orderIdStr = metadata != null ? metadata.get("dispute_id") : null;
            if (orderIdStr == null || orderIdStr.isBlank()) {
                throw new IllegalStateException("Failed event payload metadata does not contain an orderId");
            }
            Long orderId = Long.valueOf(orderIdStr);

            // 2. Retrieve definitive state from Stripe API out-of-transaction bounds
            PaymentIntent intent = PaymentIntent.retrieve(paymentIntentId);
            String intentStatus = intent.getStatus();

            // Guard Clause A: If it actually succeeded, run successful finalization paths
            if ("succeeded".equals(intentStatus)) {
                log.warn("Async failure event conflicts with successful status for PaymentIntent {}. Reconciling order {}.", paymentIntentId, orderId);
                if (stripeObject instanceof Session session) {
                    handleReturnShippingPayment(session);
                } else {
                    // Reconcile raw PaymentIntent via its ID reference using your existing method
                    handleCheckoutCompleted(event);
                }
                return;
            }

            // Guard Clause B: Do not release inventory while the payment is actively processing
            if ("processing".equals(intentStatus)) {
                log.info("PaymentIntent {} is still processing. Keeping inventory reserved for order {}.", paymentIntentId, orderId);
                return;
            }

            // Guard Clause C: Only proceed with eviction if status confirms definitive failure
            if (!"canceled".equals(intentStatus) && !"requires_payment_method".equals(intentStatus)) {
                log.warn("PaymentIntent {} has an unexpected status: {}. Leaving order {} unchanged.", paymentIntentId, intentStatus, orderId);
                return;
            }
        }

    }


    protected void executeCheckoutCancellationTx(String sessionId, Long orderId) {
        Payment payment = null;

        // If we have a session ID, look up by session
        if (sessionId != null && !sessionId.isBlank()) {
            payment = paymentRepo.findByProviderSessionIdForUpdate(sessionId).orElse(null);
        }

        // Fallback: If sessionId is null (raw PaymentIntent event), look up by Order ID instead
        if (payment == null) {
            payment = paymentRepo.findByListingOrderId(orderId).stream()
                    .filter(p -> p.getProvider() == PaymentProvider.STRIPE)
                    .findFirst()
                    .orElse(null);

            // Re-lock the found payment row explicitly to guarantee safety if a session lock wasn't grabbed
            if (payment != null) {
                payment = paymentRepo.findById(payment.getId()).orElse(null);
            }
        }

        if (payment != null && payment.getStatus() == PaymentStatus.PAID) {
            log.info("Ignoring failed-payment event for already-paid session/payment ID: {}", payment.getId());
            return;
        }

        ListingOrder order = listingOrderRepo.findByIdForUpdate(orderId)
                .orElseThrow(() -> new IllegalStateException("Order not found: " + orderId));

        // Never overwrite a successful or otherwise terminal order
        if (order.getOrderStatus() == OrderStatus.PAID || order.getOrderStatus() == OrderStatus.CANCELLED) {
            return;
        }

        if (payment != null) {
            payment.setStatus(PaymentStatus.CANCELLED);
            paymentRepo.save(payment);
        }

        order.setOrderStatus(OrderStatus.CANCELLED);

        for (ListingOrderItem item : order.getItems()) {
            InventoryItem inventory = item.getInventoryItem();

            // Release only inventory that is still currently reserved
            if (inventory.getStatus() == InventoryStatus.RESERVED) {
                inventory.setStatus(InventoryStatus.AVAILABLE);
                inventory.setReservedUntil(null);
                inventory.setReservedBy(null);
                inventoryItemRepo.save(inventory);
            }
        }

        listingOrderRepo.save(order);
        log.info("Successfully cancelled unpaid order {} and released reserved inventory inside transaction scope.", orderId);
    }



    @Transactional
    public void handleCheckoutSessionExpired(Event event) throws StripeException {
        // 1. Deserialize the Checkout Session
        Object data= event.getDataObjectDeserializer().deserializeUnsafe();

        Session session = (Session) data;
        String purpose = session.getMetadata() == null
                ? null
                : session.getMetadata().get("payment_purpose");
        if (purpose == null) {
            throw new IllegalStateException(
                    "Missing payment_purpose metadata for session: "
                            + session.getId()
            );
        }
       if(purpose.equals("ORDER")){
           Map<String, String> metadata = session.getMetadata();
           String orderIdStr = metadata != null ? metadata.get("order_id") : null;

           if (orderIdStr == null || orderIdStr.isBlank()) {
               throw new IllegalStateException("Expired Checkout Session does not contain orderId: " + session.getId());
           }
           Long orderId = Long.valueOf(orderIdStr);

           String paymentIntentId = session.getPaymentIntent();
           if (paymentIntentId != null && !paymentIntentId.isBlank()) {
               PaymentIntent intent = PaymentIntent.retrieve(paymentIntentId);

               // Guard A: Succeeded in the final seconds of life -> Finalize rather than evicting
               if ("succeeded".equals(intent.getStatus())) {
                   finalizePaidCheckoutSession(session);
                   return;
               }

               // Guard B: Actively processing asynchronous clearance -> Keep reservation alive
               if ("processing".equals(intent.getStatus())) {
                   log.info("PaymentIntent {} is processing. Keeping order {} reserved.", paymentIntentId, orderId);
                   return;
               }
           }

           // 2. DATABASE WORKFLOW BOUNDARY: Hand execution off to the transaction worker helper
           this.executeSessionExpirationTx(session.getId(), orderId);
       }
       if(purpose.equals("RETURN_SHIPPING")){
          handleReturnShippingCheckoutSessionExpired(session);
       }


    }

    @Transactional
    public void handleReturnShippingCheckoutSessionExpired(Session session){
        Map<String, String> metadata = session.getMetadata();
        String orderIdStr = metadata != null ? metadata.get("dispute_id") : null;

        if (orderIdStr == null || orderIdStr.isBlank()) {
            throw new IllegalStateException("Expired Checkout Session does not contain disputeId: " + orderIdStr);
        }
        Long orderId = Long.valueOf(orderIdStr);
        Payment payment = paymentRepo.findByProviderSessionId(session.getId()).orElse(null);
        if(payment!=null){
            payment.setStatus(PaymentStatus.CANCELLED);
            paymentRepo.save(payment);
        }

        //update dispute to approved by seller for return
        OrderDispute orderDispute = orderDisputeRepo.findByIdForUpdate(orderId).orElse(null);
        if(orderDispute!=null && orderDispute.getDisputeStatus()!=DisputeStatus.APPROVED){
            orderDispute.setDisputeStatus(DisputeStatus.OPEN);
            orderDisputeRepo.save(orderDispute);

            //create shipping
            shipmentService.createReturnShipment(orderDispute);
        }
    }

    protected void executeSessionExpirationTx(String sessionId, Long orderId) {
        // 1. Lock down the targeted payment tracker row first
        Payment payment = paymentRepo.findByProviderSessionIdForUpdate(sessionId).orElse(null);

        if (payment == null) {
            log.info("No associated payment tracking record found for session {}", sessionId);
            return;
        }

        if (payment.getStatus() == PaymentStatus.PAID) {
            log.info("Ignoring expired Checkout Session for already-paid payment: {}", sessionId);
            return;
        }

        // 2. Lock down the parent order row
        ListingOrder order = listingOrderRepo.findByIdForUpdate(orderId)
                .orElseThrow(() -> new IllegalStateException("Order not found: " + orderId));

        // If order has already transitioned into a terminal phase, exit early safely
        if (order.getOrderStatus() == OrderStatus.PAID || order.getOrderStatus() == OrderStatus.CANCELLED) {
            return;
        }

        log.info("Stripe Session {} is expired. Evicting and cancelling order #{}", sessionId, orderId);

        // 3. Mark transaction trackers as permanently terminated
        payment.setStatus(PaymentStatus.FAILED);
        paymentRepo.save(payment);

        order.setOrderStatus(OrderStatus.CANCELLED);

        // 4. Securely clear reservations using row locks to eliminate dirty read race-conditions
        for (ListingOrderItem item : order.getItems()) {
            InventoryItem inventory = item.getInventoryItem();

            // Explicitly load and lock each physical item row directly from the database table
            InventoryItem lockedInventory = inventoryItemRepo.findByIdForUpdate(inventory.getId())
                    .orElseThrow(() -> new IllegalStateException("Inventory item missing from system database catalog: " + inventory.getId()));

            // Return items back to available shopping circulation pools safely
            if (lockedInventory.getStatus() == InventoryStatus.RESERVED) {
                lockedInventory.setStatus(InventoryStatus.AVAILABLE);
                lockedInventory.setReservedUntil(null); // Wipe reservation timestamps completely
                lockedInventory.setReservedBy(null);
                inventoryItemRepo.save(lockedInventory);
            }

            item.setOrderItemStatus(OrderItemStatus.CANCELLED);
            listingOrderItemRepo.save(item);
        }

        listingOrderRepo.save(order);
        log.info("Expired Checkout Session successfully handled. Returned items for order {} back to store catalog.", order.getOrderNumber());
    }


    public void handleCheckoutSessionExpired(Session session) throws StripeException {

        Map<String, String> metadata = session.getMetadata();
        String orderId = metadata != null ? metadata.get("order_id") : null;

        if (orderId == null || orderId.isBlank()) {
            throw new IllegalStateException(
                    "Expired Checkout Session does not contain orderId: " + session.getId()
            );
        }

        // Lock payment first, using the same lock order as finalization.
        Payment payment = paymentRepo
                .findByProviderSessionIdForUpdate(session.getId())
                .orElse(null);

        if (payment == null) {
            log.info("No associated payment for this session {}", session.getId());
            return;
        }

        if (payment.getStatus() == PaymentStatus.PAID) {
            log.info("Ignoring expired Checkout Session for already-paid payment: {}", session.getId());
            return;
        }

        ListingOrder order = listingOrderRepo
                .findByIdForUpdate(Long.valueOf(orderId))
                .orElseThrow(() -> new IllegalStateException(
                        "Order not found: " + orderId
                ));

        // If the order is already in a terminal state, exit early cleanly
        if (order.getOrderStatus() == OrderStatus.PAID || order.getOrderStatus() == OrderStatus.CANCELLED) {
            return;
        }

        // If the PaymentIntent has already succeeded, finalize the order instead of cancelling.
        String paymentIntentId = session.getPaymentIntent();
        if (paymentIntentId != null && !paymentIntentId.isBlank()) {

            //  lookup stripe for payment
            PaymentIntent intent = PaymentIntent.retrieve(paymentIntentId);

            if ("succeeded".equals(intent.getStatus())) {
                finalizePaidCheckoutSession(session);
                return;
            }

            if ("processing".equals(intent.getStatus())) {
                log.info("PaymentIntent {} is processing. Keeping order {} reserved.", paymentIntentId, orderId);
                return;
            }
        }

        /* ==========================================================================
         *  TERMINATE DEAD SESSION RESERVATIONS
         * ========================================================================== */
        log.info("Stripe Session {} is expired. Evicting and cancelling order #{}", session.getId(), orderId);

        // 1. Terminate payment row records
        payment.setStatus(PaymentStatus.FAILED); // Marks payment as permanently failed
        paymentRepo.save(payment);

        // 2. Terminate the parent listing order row status
        order.setOrderStatus(OrderStatus.CANCELLED); // Switch to CANCELLED instead of PROCESSING

        // 3. Restock single-inventory items back into the catalog safely
        for (ListingOrderItem item : order.getItems()) {
            InventoryItem inventory = item.getInventoryItem();

            // Return items back to available shopping circulation pools instantly
            inventory.setStatus(InventoryStatus.AVAILABLE);
            inventory.setReservedUntil(null); // Wipe old reservation timestamps completely
            inventory.setReservedBy(null);
            inventoryItemRepo.save(inventory);

            item.setOrderItemStatus(OrderItemStatus.CANCELLED);
            listingOrderItemRepo.save(item);
        }

        listingOrderRepo.save(order);

        log.info("Expired Checkout Session successfully handled. Returned items for order {} back to store catalog.",
                order.getOrderNumber());
    }



    @Scheduled(fixedDelay = 30_000)
    @org.springframework.transaction.annotation.Transactional
    public void reconcilePendingPayments() {

        Instant cutoff = Instant.now().minusSeconds(300);

        List<Payment> pendingPayments =
                paymentRepo.findByStatusAndProviderAndCreatedAtBeforeForUpdate(
                        PaymentStatus.PENDING,
                        PaymentProvider.STRIPE,
                        cutoff
                );

        for (Payment payment : pendingPayments) {
            try {
                reconcilePayment(payment.getId());
            } catch (Exception e) {
                log.error(
                        "Failed to reconcile payment {}. It will be retried.",
                        payment.getId(),
                        e
                );
            }
        }
    }


    public void reconcilePayment(Long paymentId) {

        Payment payment = paymentRepo.findById(paymentId)
                .orElseThrow(() -> new IllegalStateException(
                        "Payment not found: " + paymentId
                ));

        // Another webhook or worker may already have processed it.
        if (payment.getStatus() != PaymentStatus.PENDING) {
            return;
        }

        String sessionId = payment.getProviderSessionId();


        if (sessionId == null || sessionId.isBlank()) {
            log.warn("Pending payment {} has no Stripe Session ID", paymentId);
            return;
        }

        // Query Stripe outside the database transaction.
        Session session = null;
        try {

            session = Session.retrieve(sessionId);
        } catch (StripeException e) {
            log.error("No session created :{}",e.getMessage());
        }
        if(session==null){
            return;
        }

        String purpose = session.getMetadata() == null
                ? null
                : session.getMetadata().get("payment_purpose");
        if (purpose == null) {
            throw new IllegalStateException(
                    "Missing payment_purpose metadata for session: "
                            + session.getId()
            );
        }

        if(purpose.equals("ORDER")){
            if ("paid".equals(session.getPaymentStatus())) {

                // Use your existing idempotent successful-payment finalizer.
                finalizePaidCheckoutReconciliation(session);


                log.info("Reconciled successful payment {}", paymentId);
                return;
            }

            if ("expired".equals(session.getStatus())) {

                //
                try {
                    handleCheckoutSessionExpired(session);
                } catch (StripeException e) {
                    throw new RuntimeException(e);
                }
                return;
            }
        }
        if(purpose.equals("RETURN_SHIPPING")){
            if ("paid".equals(session.getPaymentStatus())) {

                // Use your existing idempotent successful-payment finalizer.
                try {
                    handleReturnShippingPayment(session);
                } catch (StripeException e) {
                    throw new RuntimeException(e);
                }

                log.info("Reconciled successful payment {}", paymentId);
                return;
            }

            if ("expired".equals(session.getStatus())) {

                //session expired and cancel payment
                handleReturnShippingCheckoutSessionExpired(session);
                return;
            }
        }



        // Open, complete-but-unpaid, or other unresolved states:
        // leave the payment unchanged and reconcile again later.
        log.info(
                "Payment {} remains unresolved: sessionStatus={}, paymentStatus={}",
                paymentId,
                session.getStatus(),
                session.getPaymentStatus()
        );



    }




    public void approvePayout(Long payoutId) {
        SellerPayout payout = sellerPayoutService
                .getSellerPayoutById(payoutId);

        if (payout.getStatus() != PayoutStatus.READY_FOR_REVIEW) {
            throw new IllegalStateException(
                    "Payout is not awaiting review"
            );
        }

        ListingOrderItem item = payout.getListingOrderItem();

        // Both conditions must be satisfied.
        if (item.getOrderItemStatus() != OrderItemStatus.PAID
                || item.getReceiptConfirmationStatus()
                != ReceiptConfirmationStatus.CONFIRMED) {
            throw new IllegalStateException(
                    "Payout not allowed: item must be paid and receipt confirmed"
            );
        }

        // Validate shipment exists and has been delivered.
        SellerShipment shipment = shipmentService.getShipments(item.getId());

        if (shipment == null
                || shipment.getShipmentStatus() != ShipmentStatus.DELIVERED) {
            throw new IllegalStateException(
                    "Payout not allowed: shipment has not been delivered"
            );
        }

        // Validate the seller's connected Stripe account.
        String accountId = item.getSeller().getStripeAccountId();

        if (accountId == null || accountId.isBlank()) {
            throw new IllegalStateException(
                    "Payout not allowed: seller has no Stripe account"
            );
        }

        // Validate the seller's payout amount.
        BigDecimal sellerPrice = item.getSellerPrice();

        if (sellerPrice == null || sellerPrice.signum() <= 0) {
            throw new IllegalStateException(
                    "Payout not allowed: invalid seller amount"
            );
        }

        // Validate that the associated order payment exists and is paid.
        Payment payment = paymentRepo
                .findByListingOrderIdAndStatus(
                        item.getListingOrder().getId(),
                        PaymentStatus.PAID
                )
                .orElseThrow(() -> new IllegalStateException(
                        "Payout not allowed: no successful order payment"
                ));

        if (payment.getStripeChargeId() == null
                || payment.getStripeChargeId().isBlank()) {
            throw new IllegalStateException(
                    "Payout not allowed: Stripe charge is missing"
            );
        }

        payout.setStatus(PayoutStatus.APPROVED);
        sellerPayoutService.save(payout);
    }

}

