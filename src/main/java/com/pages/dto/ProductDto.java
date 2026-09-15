package com.pages.dto;

import com.pages.model.Brand;
import com.pages.model.Category;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProductDto {

    private Brand brand;

    private Category category;

    private String name;

    private String slug;

    private String description;

    private boolean active;
}
