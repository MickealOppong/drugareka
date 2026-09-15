package com.pages.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProductData {

    private Long id;
    @NotNull(message = "Name cannot be null")
    @NotBlank(message = "Name cannot be empty")
    private String name;

    @NotNull(message = "Status cannot be null")
    @NotBlank(message = "Status cannot be empty")
    private String status;

    @NotNull(message = "Category cannot be null")
    @NotBlank(message = "Category cannot be empty")
    private String category;

    @NotNull(message = "Condition cannot be null")
    @NotBlank(message = "Please set product condition")
    private String condition;

    @NotNull(message = "Description cannot be null")
    @NotBlank(message = "Description cannot be empty")
    private String description;

    @NotNull(message = "Cena nie moze byc pusta")
    @DecimalMin(
            value = "0.00",
            inclusive = false,
            message = "Cena produktu musi być większa od zera"
    )
    private BigDecimal price;

    @NotNull(message = "Shipping cannot be null")
    @NotBlank(message = "Please provide shipping information")
    private String shippingInfo;

    private String sku;

    @NotNull(message = "Brand cannot be null")
    @NotBlank(message = "Brand cannot be empty")
    private String brand;

    private MultipartFile[] images;
    private Integer[] imageSortOrder;

    @NotNull(message = "Shipping method cannot be null")
    @NotBlank(message = "Please provide shipping method")
    private String shippingMethod;
}
