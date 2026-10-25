package com.pages.dto;

import com.pages.enums.DisputeStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DisputeResponse {
    private Long id;
    private String ticketId;
    private String orderNumber;
    private String user;
    private DisputeStatus status;
    private String sellerComment;
    private String buyerComment;
    private String reason;
    private Instant createdAt;
    private List<String> images;
    private List<String> listingImages;
}
