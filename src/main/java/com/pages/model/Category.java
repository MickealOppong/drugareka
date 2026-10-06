package com.pages.model;

import com.pages.enums.Status;
import com.pages.util.LogEntity;
import com.pages.util.Media;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "categories", uniqueConstraints = {
        // This physically allows "Shoes" to belong to Parent ID 1 (Men) AND Parent ID 2 (Women) simultaneously!
        @UniqueConstraint(name = "uk_category_name_per_parent", columnNames = {"name", "parent_id"}),
        @UniqueConstraint(name = "uk_category_slug_per_parent", columnNames = {"slug", "parent_id"})
})
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Category extends LogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String slug;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Category parent;

    private String path;


    @Column(nullable = false)
    private Boolean isActive = true;

    private Integer sortOrder;

    // getters/setters
}
