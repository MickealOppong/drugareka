package com.pages.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class PayUPayMethod {

    private String type;

    private String value;
}