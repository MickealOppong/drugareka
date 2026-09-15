package com.pages.dto;

import com.pages.enums.ComplaintStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ComplaintResponse {
    private Long id;
    private String ticketId;
    private String orderNumber;
    private String user;
    private String issue;
    private String description;
    private ComplaintStatus status;
    private Instant createdAt;
}
