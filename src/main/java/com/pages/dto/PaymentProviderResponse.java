package com.pages.dto;

import lombok.*;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PaymentProviderResponse {

    private String transactionId;

    private String paymentUrl;
}
