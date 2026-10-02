package com.pages.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Sender {

    private String name;
    private String street;
    private String postalCode;
    private String city;
    private String country;
    private String phone;
    private String email;
}
