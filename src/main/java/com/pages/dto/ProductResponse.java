package com.pages.dto;

import com.pages.model.Brand;
import com.pages.model.Category;
import lombok.*;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ProductResponse {


    private Long id;

    private Brand brand;

    private Category category;

    private String name;

    private String slug;

    private String description;



}
