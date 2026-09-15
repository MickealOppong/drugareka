package com.pages.dto;

import lombok.Builder;

@Builder
public record AddressResponse(String city, String country, String street, String postalCode,String contact) {
}
