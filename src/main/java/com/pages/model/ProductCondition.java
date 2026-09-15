package com.pages.model;

import com.pages.dto.ConditionRequest;
import com.pages.util.LogEntity;
import com.pages.util.UtilService;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

@Entity
@Data
@EqualsAndHashCode(callSuper = false)
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ProductCondition extends LogEntity {

    @Id @GeneratedValue
    private Long id;
    private String name;
    private String slug;
    private String description;
    private Integer sortOrder;
    private boolean active;

    public ProductCondition(String name, String description, String slug, int sortOrder, boolean active) {
        this.name = name;
        this.sortOrder = sortOrder;
        this.description = description;
        this.active = active;
        this.slug = UtilService.formatNameToSlug(slug);
    }

    public ProductCondition(ConditionRequest conditionRequest){
        this.name = conditionRequest.getName();
        this.description = conditionRequest.getDescription();
        this.sortOrder = conditionRequest.getSortOrder();
        this.active = conditionRequest.isActive();
        this.slug = UtilService.formatNameToSlug(conditionRequest.getSlug());
    }


}
