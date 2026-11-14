package com.pages.dto;

import lombok.Data;

@Data
public class SellerProfileUpdate {
    private Long sellerId;
    private String status;
    private String reason;
}
