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
import com.pages.util.Notification;
import com.pages.util.UtilService;
import com.stripe.exception.StripeException;
import com.stripe.model.Charge;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.checkout.Session;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
    private final AppUserDetailsService appUserDetailsService;
    private final ListingOrderItemRepo listingOrderItemRepo;
    private final SellerShipmentTokenService sellerShipmentTokenService;
    private final MessageSource messageSource;
    private final ServiceChargeService serviceChargeService;

    public PaymentService(PaymentRepo paymentRepo, ListingOrderRepo listingOrderRepo, CartService cartService, SellerPayoutService sellerPayoutService, InventoryItemRepo inventoryItemRepo, ShipmentService shipmentService, SellerProfileService sellerProfileService, EmailNotificationService emailNotificationService,
                          AppUserDetailsService appUserDetailsService, ListingOrderItemRepo listingOrderItemRepo, SellerShipmentTokenService sellerShipmentTokenService, MessageSource messageSource, ServiceChargeService serviceChargeService) {
        this.paymentRepo = paymentRepo;
        this.listingOrderRepo = listingOrderRepo;
        this.cartService = cartService;
        this.sellerPayoutService = sellerPayoutService;
        this.inventoryItemRepo = inventoryItemRepo;
        this.shipmentService = shipmentService;
        this.sellerProfileService = sellerProfileService;
        this.emailNotificationService = emailNotificationService;
        this.appUserDetailsService = appUserDetailsService;
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
    public ResponseDto<String> createPayment(Session session,ListingOrder listingOrder) {

        try {

            Payment payment = Payment.builder()
                    .listingOrder(listingOrder)
                    .status(PaymentStatus.PENDING)
                    .amount(listingOrder.getOrderTotal())
                    .currency("PLN")
                    .providerSessionId(session.getId())
                    .stripePaymentIntentId(session.getPaymentIntent())
                    .provider(PaymentProvider.STRIPE)
                    .build();

            paymentRepo.save(payment);

            // -------------------------------------------------
            // 6. Return client secret
            // -------------------------------------------------

            return ResponseDto.<String>builder()
                    .data(session.getClientSecret())
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

        List<EmailProductItemDto> emailDto =listingOrderItemRepo.findByListingOrderId(order.getId())
                .stream().map(item->{

                    return EmailProductItemDto.builder()
                            .amount(item.getSellerPrice())
                            .serviceCharge(item.getServiceCharge())
                            .shippingCost(item.getShippingCost())
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
    public PaymentStatus getPaymentStatus(String orderNumber){

      return paymentRepo.findByOrderNumber(orderNumber).map(Payment::getStatus)
                .orElseThrow(()->new ResponseStatusException(HttpStatus.BAD_REQUEST
                        ,getMessage("api.errors.payment.error")));
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
    public void handleCheckoutCompleted(Event event)
            throws StripeException {


                     Object data= event.getDataObjectDeserializer().deserializeUnsafe();

        Session session = (Session) data;

        String orderId = session.getMetadata().get("orderId");

        if (orderId == null) {
            throw new IllegalStateException("Stripe session does not contain orderId");
        }


        ListingOrder order =
               listingOrderRepo
                        .findById(Long.valueOf(orderId))
                        .orElseThrow();


        Payment payment =
                paymentRepo
                        .findByProviderSessionId(session.getId())
                        .orElseThrow();

        /*
         * Idempotency.
         *
         * Stripe can deliver the same webhook more than once.
         */

        if (payment.getStatus() == PaymentStatus.PAID) {
            return;
        }

        String paymentIntentId = session.getPaymentIntent();
        PaymentIntent paymentIntent = PaymentIntent.retrieve(paymentIntentId);

            Charge latestCharge = paymentIntent.getLatestChargeObject();
            String receiptUrl = latestCharge!=null?latestCharge.getReceiptUrl():null;


        payment.setStatus(PaymentStatus.PAID);
        payment.setPaidAt(Instant.now());
        payment.setStripePaymentIntentId(session.getPaymentIntent());

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

        shipmentService.createShipment(order.getItems());
         cartService.deleteCartById(order.getBuyer().getId());

         //BUYER EMAIL NOTIFICATION
        String buyerName = listingOrder.getBuyerNameSnapshot();
        String buyerEmail = listingOrder.getBuyer().getUsername();

        List<EmailProductItemDto> emailDto =listingOrderItemRepo.findByListingOrderId(order.getId())
                .stream().map(item->{

                    return EmailProductItemDto.builder()
                            .amount(item.getSellerPrice())
                            .shippingCost(item.getShippingCost())
                            .serviceCharge(item.getServiceCharge())
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
                        .serviceCharge(item.getServiceCharge())
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
    public void handleCheckoutExpired(Event event) {

        Object data= event.getDataObjectDeserializer()
                .getObject()
                .orElseThrow();

        Session session = (Session) data;

        String orderId = session.getMetadata().get("orderId");

        ListingOrder order = listingOrderRepo
                .findById(Long.valueOf(orderId))
                .orElseThrow();

        // Don't modify an order that was already paid.
        if (order.getOrderStatus() == OrderStatus.PAID) {
            return;
        }

        order.setOrderStatus(OrderStatus.CANCELLED);

        Payment payment = paymentRepo
                .findByProviderSessionId(session.getId())
                .orElse(null);

        if (payment != null) {
            payment.setStatus(PaymentStatus.CANCELLED);
        }

        for (ListingOrderItem item : order.getItems()) {

            InventoryItem inventory =
                    item.getInventoryItem();

            if (inventory.getStatus() == InventoryStatus.RESERVED) {

                inventory.setStatus(InventoryStatus.AVAILABLE);

                inventory.setReservedUntil(null);
            }
        }

        listingOrderRepo.save(order);

        if (payment != null) {
            paymentRepo.save(payment);
        }
    }

}
