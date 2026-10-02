package com.pages.dto;

import com.pages.enums.PayUPaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PayUOrderResponse {

    private PayUStatusResponse status;

    private String redirectUri;

    private String orderId;

    private String extOrderId;
}
