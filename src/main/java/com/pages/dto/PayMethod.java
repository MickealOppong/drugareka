package com.pages.dto;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class PayMethod {
    private String type;
    private String value;
}
