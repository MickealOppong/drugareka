package com.pages.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
public class ListPageUsers {

    List<UserDetailsDto> userList;
    private int pageSize;
    private int page;
    private int totalPages;
    private int accumulatedItems;
    private long totalElements;
}
