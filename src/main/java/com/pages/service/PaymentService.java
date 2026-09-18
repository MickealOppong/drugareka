package com.pages.service;

import com.pages.dto.EmailProductItemDto;
import com.pages.dto.ResponseDto;
import com.pages.enums.InventoryStatus;
import com.pages.enums.OrderStatus;
import com.pages.enums.PaymentStatus;
import com.pages.model.*;
import com.pages.repository.InventoryItemRepo;
import com.pages.repository.ListingOrderItemRepo;
import com.pages.repository.ListingOrderRepo;
import com.pages.repository.PaymentRepo;
import com.stripe.exception.StripeException;
import com.stripe.model.Charge;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.checkout.Session;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

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
    private final InventoryItemPriceService inventoryItemPriceService;

    public PaymentService(PaymentRepo paymentRepo, ListingOrderRepo listingOrderRepo, CartService cartService, SellerPayoutService sellerPayoutService, InventoryItemRepo inventoryItemRepo, ShipmentService shipmentService, SellerProfileService sellerProfileService, EmailNotificationService emailNotificationService, AppUserDetailsService appUserDetailsService, ListingOrderItemRepo listingOrderItemRepo, InventoryItemPriceService inventoryItemPriceService) {
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
        this.inventoryItemPriceService = inventoryItemPriceService;
    }


    @Transactional
    public ResponseDto<String> createPayment(Session session,ListingOrder listingOrder) {

        try {

            Payment payment = Payment.builder()
                    .listingOrder(listingOrder)
                    .status(PaymentStatus.PENDING)
                    .amount(listingOrder.getOrderTotal())
                    .currency("PLN")
                    .stripeSessionId(session.getId())
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
                        .findByStripeSessionId(session.getId())
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
        payment.setReceiptUrl(receiptUrl);

        order.setOrderStatus(OrderStatus.PAID);
        order.setPaidAt(Instant.now());

        order.getItems().forEach(orderItem -> {

            InventoryItem inventory = orderItem.getInventoryItem();

            inventory.setStatus(InventoryStatus.SOLD);

            inventory.setReservedUntil(null);

            inventoryItemRepo.save(inventory);
        });

        paymentRepo.save(payment);

        listingOrderRepo.save(order);

        sellerPayoutService.createPayouts(order);
        sellerProfileService.updateSellerTotalPayout(order.getItems());

        shipmentService.createShipment(order.getItems());
         cartService.deleteCartById(order.getBuyerId());

         //buyer notification
        AppUser buyer = appUserDetailsService.getAppUserId(order.getBuyerId());


        String buyerName = buyer.getFirstName()+" "+buyer.getLastName();
        List<EmailProductItemDto> emailDto =listingOrderItemRepo.findByListingOrderId(order.getId())
                .stream().map(item->{

                    return EmailProductItemDto.builder()
                            .amount(item.getFinalizedPrice())
                            .productName(item.getInventoryItem().getProductCatalog().getName())
                            .build();
                }).toList();

        emailNotificationService.sendBulkOrderConfirmationToBuyer(buyer.getUsername(),buyerName,order.getOrderNumber(),emailDto,order.getOrderTotal());

         //send action to seller
       //address
        String address = String.join(",","kasoa.pl",order.getShippingAddress());

        Map<SellerProfile, List<ListingOrderItem> > itemsGroupedBySeller = listingOrderItemRepo.findByListingOrderId(order.getId()).stream()
                .collect(Collectors.groupingBy(ListingOrderItem::getSeller));



        itemsGroupedBySeller.forEach((key, value) -> {
            String sellerName = key.getUser().getFirstName()+" "+key.getUser().getLastName();

            List<EmailProductItemDto> dto = value.stream().map(item -> {

                BigDecimal toPay = inventoryItemPriceService.getPrices(item.getInventoryItem().getId()).getSellerNewPrice();

                return EmailProductItemDto.builder()
                        .productName(item.getInventoryItem().getProductCatalog().getName())
                        .amount(toPay)
                        .shippingCost(item.getShippingCost())
                        .quantity(1L)
                        .build();
            }).toList();
            String sellerEmail = key.getUser().getUsername();
            String buyerAddress = String.join(",",order.getOrderNumber(),order.getShippingAddress());
            String corporateAddress = String.join(",","Corporate Headquarters","Ul Polna 1A","00-903","Piotrkow Trybunalski","Poland");

            emailNotificationService.sendBulkOrderActionToSeller(sellerEmail,sellerName, order.getOrderNumber(),dto,buyerAddress,corporateAddress);
            //update buyer of product awaiting shipment

        });
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
                .findByStripeSessionId(session.getId())
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
