package com.pages.dto;


import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class CategoryTreeDto {
    private Long id;
    private String name;
    private String slug;
    private String path;
    private List<SubCategoryDto> subCategories;

    @Data
    @Builder
    public static class SubCategoryDto {
        private Long id;
        private String name;
        private String slug;
        private String path;
        private String parent;
    }
}

