package com.pages.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ListComplaintPage {

    List<DisputeResponse> complaintResponseList;
    private int pageSize;
    private int page;
    private int totalPages;
    private int accumulatedItems;
    private long totalElements;

}
