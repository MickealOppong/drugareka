package com.pages.dto;

import com.pages.enums.DisputeStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ComplaintRequest {
    private String issue;
    private String description;
    private Long orderId;
    @Enumerated(EnumType.STRING)
    private DisputeStatus disputeStatus;
}
