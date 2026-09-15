package com.pages.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CategoryDto {

    private String name;
    private String slug;
    private Long id;
    private String parent;
    private boolean active;
    private Integer sortOrder;
}
