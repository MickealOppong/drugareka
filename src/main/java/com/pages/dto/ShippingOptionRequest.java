package com.pages.dto;

import com.pages.enums.ItemSize;
import com.pages.enums.ShippingMethod;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@Builder
public class ShippingOptionRequest {

    private Long id;

    @NotBlank(message = "Please select item size")
    private String itemSize;

    @NotBlank(message = "Please select shipping method")
    private String shippingMethod;

    @DecimalMin(value = "0.0", inclusive = false)
    @Digits(integer=3, fraction=2)
    private BigDecimal price;


    private Boolean active;
}
