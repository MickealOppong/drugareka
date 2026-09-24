package com.pages.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InPostAddress {
    private String name;

    private String companyName;

    private String street;

    private String buildingNumber;

    private String flatNumber;

    private String postalCode;

    private String city;

    private String countryCode;

    private String email;

    private String phone;
}
