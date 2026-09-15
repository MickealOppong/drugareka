package com.pages.dto;

import com.pages.model.Brand;
import com.pages.model.Category;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ProductRequest {


    private String brand;

    private String category;

    private String name;

    private String slug;

    private String description;

}
