package com.pages.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class PayUNotificationOrder {

    private String orderId;

    private String extOrderId;

    private String orderCreateDate;

    private String notifyUrl;

    private String customerIp;

    private String merchantPosId;

    private String description;

    private String additionalDescription;

    private String currencyCode;

    private String totalAmount;

    private PayUBuyer buyer;

    private List<PayUProduct> products;

    private String status;

    private PayUPayMethod payMethod;
}
