package com.pages.interfaces;

import com.pages.dto.PaymentProviderResponse;
import com.pages.model.ListingOrder;
import com.pages.model.Payment;

public interface PaymentProvider {

    PaymentProviderResponse createPayment(
            ListingOrder listingOrder,
            Payment payment
    );
}
