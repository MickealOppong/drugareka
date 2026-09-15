package com.pages.util;

import com.pages.model.Category;
import com.pages.model.InventoryItem;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.MediaType;

@Entity
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Media {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "inventory_item_id")
    private InventoryItem inventoryItem;

    @OneToOne
    @JoinColumn(name = "category_id")
    private Category category;

    private String fileName;
    private String path;

    private Integer sortOrder;

    private String contentType;



}
