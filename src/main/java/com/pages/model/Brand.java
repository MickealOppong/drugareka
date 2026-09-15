package com.pages.model;

import com.pages.util.LogEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

@Entity
@Getter
@Setter
@ToString
@Builder
@Table(name = "Brands")
@AllArgsConstructor
@NoArgsConstructor
public class Brand extends LogEntity {

    @Id
    @GeneratedValue
    private Long id;
    private String name;
    private String slug;
    private boolean active;
    private Integer sortOrder;



    public Brand(String name, String slug,boolean active,Integer sortOrder) {
        this.name = name;
        this.slug = slug;
        this.active = active;
        this.sortOrder = sortOrder;
    }

}
