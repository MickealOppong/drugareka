package com.pages.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;
import org.springframework.web.multipart.MultipartFile;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor

public class CategoryRequest {


    @NotBlank(message = "Category name cannot be empty")
    private String name;
    private String slug;
    private Long id;
    private String parent;
    private boolean isActive;
    private Integer sortOrder;
    private MultipartFile image;
}
