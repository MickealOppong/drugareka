package com.pages.model;

import com.pages.dto.ProductDto;
import com.pages.util.LogEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class ProductCatalog extends LogEntity {

    @Id
    @GeneratedValue
    private Long id;

    @ManyToOne
    @JoinColumn(name = "brand_id",nullable = false)
    private Brand brand;

    @ManyToOne
    @JoinColumn(name = "category_id",nullable = false)
    private Category category;

    private String name;

    private String slug;

    @Column(length = 1024,columnDefinition = "TEXT")
    private String description;



    public ProductCatalog(ProductDto product){
        this.brand = product.getBrand();
        this.category = product.getCategory();
        this.name = product.getName();
        this.slug = product.getSlug();
        this.description = product.getDescription();
    }

}
