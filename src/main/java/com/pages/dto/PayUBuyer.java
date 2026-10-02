package com.pages.dto;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PayUBuyer {
    private String email;
    private String phone;
    private String name;
    private String language;
}
