package com.pages.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class PayUStatusResponse {

    private String statusCode;
    private String statusDesc;
    private String severity;
}