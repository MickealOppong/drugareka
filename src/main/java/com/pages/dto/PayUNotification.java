package com.pages.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data

public class PayUNotification {
    private PayUNotificationOrder order;

    private String localReceiptDateTime;

    private List<PayUProperty> properties;
}
