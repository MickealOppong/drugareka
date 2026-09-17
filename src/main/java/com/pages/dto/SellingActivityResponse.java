package com.pages.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SellingActivityResponse {

    private Long id;
    private String name;
    private String type;
    private String status;
    private String path;
}
