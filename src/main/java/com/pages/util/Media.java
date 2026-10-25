
package com.pages.util;

import com.pages.model.Category;
import com.pages.model.OrderDispute;
import com.pages.model.InventoryItem;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(
        name = "media",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_media_item_file_hash",
                        columnNames = {"inventory_item_id", "file_hash"}
                )
        }
)
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

    @ManyToOne
    @JoinColumn(name = "dispute_id")
    private OrderDispute orderDispute;

    private String fileName;
    private String path;
    private Integer sortOrder;
    private String contentType;

    @Column(name = "file_hash", nullable = false)
    private String fileHash;
}
