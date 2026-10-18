package com.pages.service;

import com.pages.dto.*;
import com.pages.enums.*;
import com.pages.exception.EntityNotFoundException;
import com.pages.exception.InvalidOperationException;
import com.pages.model.*;
import com.pages.repository.InventoryItemRepo;
import com.pages.repository.ListingOrderItemRepo;
import com.pages.repository.ListingOrderRepo;
import com.pages.repository.PaymentRepo;
import com.pages.util.UtilService;
import com.stripe.exception.ApiConnectionException;
import com.stripe.exception.StripeException;
import com.stripe.model.*;
import com.stripe.model.checkout.Session;
import com.stripe.model.checkout.SessionCollection;
import com.stripe.net.RequestOptions;
import com.stripe.param.RefundCreateParams;
import com.stripe.param.TransferCreateParams;
import com.stripe.param.checkout.SessionListParams;
import com.stripe.param.checkout.SessionRetrieveParams;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PaymentService {

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

    public PaymentService(PaymentRepo paymentRepo, ListingOrderRepo listingOrderRepo, CartService cartService, SellerPayoutService sellerPayoutService, InventoryItemRepo inventoryItemRepo, ShipmentService shipmentService, SellerProfileService sellerProfileService, EmailNotificationService emailNotificationService, ListingOrderItemRepo listingOrderItemRepo, SellerShipmentTokenService sellerShipmentTokenService, MessageSource messageSource, ServiceChargeService serviceChargeService) {
        this.paymentRepo = paymentRepo;
        this.listingOrderRepo = listingOrderRepo;
        this.cartService = cartService;
        this.sellerPayoutService = sellerPayoutService;
        this.inventoryItemRepo = inventoryItemRepo;
        this.shipmentService = shipmentService;
        this.sellerProfileService = sellerProfileService;
        this.emailNotificationService = emailNotificationService;
        this.listingOrderItemRepo = listingOrderItemRepo;
        this.sellerShipmentTokenService = sellerShipmentTokenService;
        this.messageSource = messageSource;
        this.serviceChargeService = serviceChargeService;
    }
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


    /*

    @Transactional
    public void handleCheckoutCompleted(Event event) throws StripeException {

        // 1. Deserialize the Checkout Session
        Object data= event.getDataObjectDeserializer().deserializeUnsafe();

        Session session = (Session) data;

        if(session==null){
           throw  new IllegalStateException(
                    "Unable to deserialize Stripe Checkout Session"
            );
        }

        // 2. Do not fulfil an unpaid or pending Checkout Session
        if (!"paid".equals(session.getPaymentStatus())) {
            log.info(
                    "Checkout is not paid. Session: {}, payment status: {}",
                    session.getId(),
                    session.getPaymentStatus()
            );
            return;
        }

        // 3. Get the order ID from Stripe metadata
        Map<String, String> metadata = session.getMetadata();

        String orderId = metadata != null
                ? metadata.get("orderId")
                : null;

        if (orderId == null || orderId.isBlank()) {
            throw new IllegalStateException(
                    "Stripe Checkout Session does not contain orderId"
            );
        }

        log.info("Finalizing Stripe payment for order: {}", orderId);

        // 4. Lock the payment row to prevent concurrent finalization
        Payment payment = paymentRepo
                .findByProviderSessionIdForUpdate(session.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "Payment not found for Stripe session: " + session.getId()
                ));

        // 5. Idempotency: do not fulfil the same payment twice
        if (payment.getStatus() == PaymentStatus.PAID) {
            log.info("Payment already finalized for session: {}", session.getId());
            return;
        }

        ListingOrder order = listingOrderRepo
                .findById(Long.valueOf(orderId))
                .orElseThrow(() -> new IllegalStateException(
                        "Order not found: " + orderId
                ));

        // 6. Record successful payment
        Instant paidAt = Instant.now();

        payment.setStatus(PaymentStatus.PAID);
        payment.setPaidAt(paidAt);
        payment.setStripePaymentIntentId(session.getPaymentIntent());

        order.setOrderStatus(OrderStatus.PAID);
        order.setPaidAt(paidAt);

        // 7. Mark inventory and order items as paid/sold
        order.getItems().forEach(orderItem -> {
            InventoryItem inventory = orderItem.getInventoryItem();

            inventory.setStatus(InventoryStatus.SOLD);
            inventory.setReservedUntil(null);

            inventoryItemRepo.save(inventory);

            orderItem.setOrderItemStatus(OrderItemStatus.PAID);
            listingOrderItemRepo.save(orderItem);
        });

        paymentRepo.save(payment);
        ListingOrder listingOrder = listingOrderRepo.save(order);

        // 8. Create seller payouts
        sellerPayoutService.createPayouts(listingOrder);

        // 9. Update seller sales totals
        sellerProfileService.updateSellerTotalSales(listingOrder.getItems());

        // 10. Create shipment records
        shipmentService.createShipment(listingOrder.getItems());

        // 11. Clear the buyer's cart
        cartService.deleteCartById(listingOrder.getBuyer().getId());

        // 12. Send buyer confirmation email
       sendNotifications((listingOrder));
        log.info(
                "Successfully finalized payment for order: {}",
                listingOrder.getOrderNumber()
        );
    }

     */

    @Transactional
    public void  handleCheckoutCompleted(Event event) throws StripeException {

        // 1. Deserialize the Checkout Session
        Object data= event.getDataObjectDeserializer().deserializeUnsafe();

        Session session = (Session) data;

       if(session!=null){
           finalizePaidCheckoutSession(session);
       }

    }

    /**
     * checkout charge event handler
     * @param event
     */
    public void handleChargeSucceeded(Event event) {
        try {
            Charge charge = (Charge) event
                    .getDataObjectDeserializer()
                    .deserializeUnsafe();

            String orderNumber = charge.getTransferGroup();

            if (orderNumber == null || orderNumber.isBlank()) {
                throw new IllegalStateException(
                        "Charge has no transfer group: " + charge.getId()
                );
            }

            Payment payment = paymentRepo
                    .findByOrderNumber(orderNumber)
                    .orElseThrow(() -> new EntityNotFoundException(
                            "Payment not found for order number: " + orderNumber
                    ));

            payment.setStripeChargeId(charge.getId());
            payment.setStatus(PaymentStatus.PAID);
            payment.setStripePaymentIntentId(charge.getPaymentIntent());

            paymentRepo.save(payment);

        } catch (StripeException e) {
            throw new IllegalStateException(
                    "Stripe error processing event " + event.getId(), e
            );
        } catch (Exception e) {
            // Preserve the original exception and stack trace.
            log.error(
                    "Stripe webhook processing failed: {}",
                    e.getMessage(),
                    e
            );

            throw new IllegalStateException(
                    "Failed to process charge.succeeded event "
                            + event.getId(), e
            );
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
                        || record.getStatus().equals(PaymentStatus.REFUND))
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
                    .status(PaymentStatus.REFUND)
                    .amount(order.getSellerPrice().negate())
                    .provider(originalPayment.getProvider())
                    .currency(originalPayment.getCurrency())
                    .providerSessionId(originalPayment.getProviderSessionId())
                    .stripePaymentIntentId(originalPayment.getStripePaymentIntentId())
                    .build();

            paymentRepo.save(refund);
            //create refund line to reduce seller payout
            sellerPayoutService.createRefund(order);

        } catch (Exception e) {

            log.error("Failed to create refund", e);
            throw new InvalidOperationException("Failed to create refund");
        }
    }


    @Transactional
    private void finalizePaidCheckoutSession(Session session) {
        try {

            if (!"paid".equals(session.getPaymentStatus())) {
                throw new IllegalStateException(
                        "Cannot finalize unpaid Checkout Session: " + session.getId()
                );
            }

            Map<String, String> metadata = session.getMetadata();
            String orderId = metadata == null ? null : metadata.get("orderId");

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

            if (order.getOrderStatus() == OrderStatus.PAID) {
                log.info("Order {} is already paid", orderId);
                return;
            }

            String paymentIntentId = session.getPaymentIntent();

            if (paymentIntentId == null || paymentIntentId.isBlank()) {
                throw new IllegalStateException(
                        "Checkout Session has no PaymentIntent: " + session.getId()
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

            order.setOrderStatus(OrderStatus.PAID);
            order.setPaidAt(paidAt);

            for (ListingOrderItem item : order.getItems()) {
                InventoryItem inventory = getInventoryItem(item);
                inventory.setReservedUntil(null);
                inventory.setReservedBy(null);
                inventory.setStatus(InventoryStatus.SOLD);
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


    @Transactional
    private void finalizePaidCheckoutReconciliation(Session session) {
        try {

            if (!"paid".equals(session.getPaymentStatus())) {
                throw new IllegalStateException(
                        "Cannot finalize unpaid Checkout Session: " + session.getId()
                );
            }

            Map<String, String> metadata = session.getMetadata();
            String orderId = metadata == null ? null : metadata.get("orderId");

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

            order.setOrderStatus(OrderStatus.PAID);
            order.setPaidAt(paidAt);

            for (ListingOrderItem item : order.getItems()) {
                InventoryItem inventory = item.getInventoryItem();



                // Do not silently overwrite inventory sold to another buyer.
                if (inventory.getStatus() == InventoryStatus.SOLD ) {
                    throw new IllegalStateException(
                            "Inventory conflict: item " + item.getId()
                                    + " is already sold"
                    );
                }



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
        //deserialize event
     PaymentIntent paymentIntent=(PaymentIntent) event.getDataObjectDeserializer().deserializeUnsafe();
        String paymentIntentId  = paymentIntent.getId();
        String trueChargeId = paymentIntent.getLatestCharge();

        // 1. Trace the parent payment tracking record
        Payment payment = paymentRepo.findByStripePaymentIntentId(paymentIntentId)
                .orElseThrow(() -> new IllegalStateException("Payment tracking record missing for intent: " + paymentIntentId));

        // Preserve the critical charge ID immediately so funds are never lost in space
        payment.setStripeChargeId(trueChargeId);
        paymentRepo.save(payment);

        ListingOrder order = payment.getListingOrder();

        //  SCENARIO A: The order was already cancelled due to the timeout deadline
        if (order.getOrderStatus() == OrderStatus.CANCELLED) {
            log.warn("CRITICAL: Received delayed payment success for ALREADY CANCELLED order #{}", order.getOrderNumber());

            boolean inventoryStillAvailable = true;
            for (ListingOrderItem item : order.getItems()) {
                if (item.getInventoryItem().getStatus() == InventoryStatus.SOLD) {
                    inventoryStillAvailable = false;
                    break;
                }
            }

            if (inventoryStillAvailable) {
                // PATH 1: The item wasn't bought by someone else yet. Reverse cancellation and fulfill normally!
                log.info("Inventory is still available. Reversing cancellation for order #{}", order.getOrderNumber());
                payment.setStatus(PaymentStatus.PAID);
                payment.setPaidAt(Instant.now());
                order.setOrderStatus(OrderStatus.PAID);

                for (ListingOrderItem item : order.getItems()) {
                    InventoryItem inventory = item.getInventoryItem();
                    inventory.setStatus(InventoryStatus.SOLD);
                    inventory.setReservedBy(null);
                    inventory.setReservedUntil(null);
                    inventoryItemRepo.save(inventory);
                }
                listingOrderRepo.save(order);
                paymentRepo.save(payment);

                // Execute your standard downstream distribution workflows safely
                sellerPayoutService.createPayouts(order);
                shipmentService.createShipment(order.getItems());
                serviceChargeService.createCharge(order);
                sellerProfileService.updateSellerTotalSales(order.getItems());
                cartService.deleteCartById(order.getBuyer().getId());
                //notify seller and buyer
                sendNotifications(order);

            } else {
                //PATH 2: The item was already sold to another buyer during the delay window!
                // You MUST hold the funds on the platform account and create an automated admin alert for a refund.
                log.error("CONFLICT: Item already sold to another user. Freezing funds for order #{} for manual refund.",
                        order.getOrderNumber());

                order.setOrderStatus(OrderStatus.REFUND);
               listingOrderRepo.save(order);

                //AUTOMATED REFUND: Programmatically return the cash to the buyer via Stripe instantly
                RefundCreateParams refundParams = RefundCreateParams.builder()
                        .setCharge(trueChargeId)
                        .setReason(RefundCreateParams.Reason.FRAUDULENT) // Flags system auto-reversal parameters
                        .build();
               Refund.create(refundParams);

                order.setOrderStatus(OrderStatus.REFUND);
                listingOrderRepo.save(order);
            }
            return;
        }

        // SCENARIO B: Normal flow (Order is still pending)
        if (order.getOrderStatus() == OrderStatus.PROCESSING) {
            // Run your standard public finalizePaidCheckoutSession(session) workflow here
           handleCheckoutCompleted(event);
        }
    }




    @Transactional
    public void handleAsyncPaymentFailed(Event event) throws StripeException {

        Session eventSession = event.getDataObjectDeserializer()
                .getObject()
                .filter(Session.class::isInstance)
                .map(Session.class::cast)
                .orElseThrow(() -> new IllegalStateException(
                        "Unable to deserialize Checkout Session from async payment failure"
                ));

        // Retrieve the latest session state from Stripe.
        Session session = Session.retrieve(eventSession.getId());

        Map<String, String> metadata = session.getMetadata();

        String orderId = metadata != null
                ? metadata.get("orderId")
                : null;

        if (orderId == null || orderId.isBlank()) {
            throw new IllegalStateException(
                    "Failed Checkout Session does not contain orderId: "
                            + session.getId()
            );
        }

        String paymentIntentId = session.getPaymentIntent();

        if (paymentIntentId == null || paymentIntentId.isBlank()) {
            throw new IllegalStateException(
                    "Checkout Session has no PaymentIntent: " + session.getId()
            );
        }

        // Verify the latest PaymentIntent state.
        PaymentIntent intent = PaymentIntent.retrieve(paymentIntentId);
        String intentStatus = intent.getStatus();

        // A success event may have arrived before this failure event.
        // Never cancel or release inventory for a successful payment.
        if ("succeeded".equals(intentStatus)) {
            log.warn(
                    "Async payment failure event conflicts with successful PaymentIntent {}. "
                            + "Order {} requires successful-payment reconciliation.",
                    paymentIntentId,
                    orderId
            );

            // Invoke existing idempotent successful-payment finalizer here.
            // Do not simply return without reconciling the successful payment.
            finalizePaidCheckoutSession(session);

            return;
        }

        // Do not release inventory while the payment is still processing.
        if ("processing".equals(intentStatus)) {
            log.info(
                    "PaymentIntent {} is still processing. Keeping inventory reserved for order {}.",
                    paymentIntentId,
                    orderId
            );
            return;
        }

        // Only proceed with cancellation for a definitively unsuccessful payment.
        if (!"canceled".equals(intentStatus)
                && !"requires_payment_method".equals(intentStatus)) {
            log.warn(
                    "PaymentIntent {} has unexpected status {}. "
                            + "Keeping order {} and inventory unchanged.",
                    paymentIntentId,
                    intentStatus,
                    orderId
            );
            return;
        }

        // Use the same lock order as successful-payment finalizer.
        Payment payment = paymentRepo
                .findByProviderSessionIdForUpdate(session.getId())
                .orElse(null);

        if (payment != null && payment.getStatus() == PaymentStatus.PAID) {
            log.info(
                    "Ignoring failed-payment event for already-paid session {}",
                    session.getId()
            );
            return;
        }

        ListingOrder order = listingOrderRepo
                .findByIdForUpdate(Long.valueOf(orderId))
                .orElseThrow(() -> new IllegalStateException(
                        "Order not found: " + orderId
                ));

        // Never overwrite a successful or otherwise terminal order.
        if (order.getOrderStatus() == OrderStatus.PAID
                || order.getOrderStatus() == OrderStatus.CANCELLED) {
            return;
        }

        if (payment != null) {
            payment.setStatus(PaymentStatus.CANCELLED);
            paymentRepo.save(payment);
        }

        order.setOrderStatus(OrderStatus.CANCELLED);

        for (ListingOrderItem item : order.getItems()) {
            InventoryItem inventory = item.getInventoryItem();

            // Release only inventory still reserved.
            // Ideally also verify reservation ownership by this order.
            if (inventory.getStatus() == InventoryStatus.RESERVED) {
                inventory.setStatus(InventoryStatus.AVAILABLE);
                inventory.setReservedUntil(null);
                inventory.setReservedBy(null);
                inventoryItemRepo.save(inventory);
            }
        }

        listingOrderRepo.save(order);

        log.info(
                "Failed Checkout Session {} cancelled unpaid order {} and released reserved inventory",
                session.getId(),
                orderId
        );
    }

    @Transactional
    public void handleCheckoutSessionExpired(Event event) throws StripeException {

        Session eventSession = event.getDataObjectDeserializer()
                .getObject()
                .filter(Session.class::isInstance)
                .map(Session.class::cast)
                .orElseThrow(() -> new IllegalStateException(
                        "Unable to deserialize expired Checkout Session"
                ));

        // Retrieve the latest session state from Stripe.
        Session session = Session.retrieve(eventSession.getId());

        Map<String, String> metadata = session.getMetadata();
        String orderId = metadata != null ? metadata.get("orderId") : null;

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

    @Transactional
    public void handleCheckoutSessionExpired(Session session) throws StripeException {

        Map<String, String> metadata = session.getMetadata();
        String orderId = metadata != null ? metadata.get("orderId") : null;

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

    @Transactional
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

        log.info("session id:{}", sessionId);

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

        // Open, complete-but-unpaid, or other unresolved states:
        // leave the payment unchanged and reconcile again later.
        log.info(
                "Payment {} remains unresolved: sessionStatus={}, paymentStatus={}",
                paymentId,
                session.getStatus(),
                session.getPaymentStatus()
        );



    }




    @Transactional
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
