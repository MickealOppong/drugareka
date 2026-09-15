package com.pages.dto;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Data
public class CheckoutRequest {

    private List<Long> listingIds;
}