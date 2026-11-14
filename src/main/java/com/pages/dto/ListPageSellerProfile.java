package com.pages.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
@Data
@Builder
public class ListPageSellerProfile {
    private List<SellerProfileResponse> sellers;
    private int pageSize;
    private int page;
    private int totalPages;
    private int accumulatedItems;
    private long totalElements;
}
