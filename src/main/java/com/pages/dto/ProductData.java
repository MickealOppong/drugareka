package com.pages.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.NumberFormat;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProductData {

    private Long id;
    @NotNull(message = "{api.product.name.empty}")
    @NotBlank(message = "{api.product.name.empty}")
    private String name;

    @NotNull(message = "{api.product.status.empty}")
    @NotBlank(message = "{api.product.status.empty}")
    private String status;

    @NotNull(message = "{api.product.categoryPath.empty}")
    private String categoryPath;

    @NotNull(message = "{api.product.condition.empty}")
    @NotBlank(message = "{api.product.condition.empty}")
    private String condition;

    @NotNull(message = "{api.product.description.empty}")
    @NotBlank(message = "{api.product.description.empty}")
    private String description;

    @NotNull(message = "{api.product.price.empty}")
    @DecimalMin(
            value = "0.00",
            inclusive = false,
            message = "{api.product.price.min}"
    )
    private BigDecimal price;

    @NotNull(message = "{api.product.price.empty}")
    @DecimalMin(
            value = "0.00",
            inclusive = false,
            message = "{api.product.price.min}"
    )
    private BigDecimal minPrice;

    @NotNull(message = "{api.product.shippingInfo.empty}")
    @NotBlank(message = "{api.product.shippingInfo.empty}")
    private String shippingInfo;

    private String sku;

    @NotNull(message = "{api.product.brand.empty}")
    @NotBlank(message = "{api.product.brand.empty}")
    private String brand;

    private MultipartFile[] images;
    private Integer[] imageSortOrder;


    @NotNull(message = "{api.product.itemSize.empty}")
    @NotBlank(message = "{api.product.itemSize.empty}")
    private String itemSize;

    private String productionYear;
    private String model;
    private String width;
    private String height;
    private String depth;
    private String itemNumber;

}
