package com.pages.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
public class BrandRequest {

    @NotBlank(message = "Brand name cannot be empty")
    private String name;
    private String slug;
    private boolean active;
    private Integer sortOrder;
}
