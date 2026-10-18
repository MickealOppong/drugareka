package com.pages.dto;

import java.math.BigDecimal;
import java.util.List;

public class StripeCheckoutModel {

    public record BasketItem(
            String listingId,
            String stripeAccountId, // Target seller acct_... link
            BigDecimal price,       // Item unit price (e.g. 150.00)
            int quantity
    ) {}

    public record CheckoutRequest(
            List<BasketItem> items,
            String currency // e.g. "pln"
    ) {}

    public record PaymentIntentResponse(
            String paymentIntentId,
            String clientSecret,
            long totalAmountGrosz
    ) {}

    public record SplitTransferRequest(
            String paymentIntentId,
            List<BasketItem> items
    ) {}
}
