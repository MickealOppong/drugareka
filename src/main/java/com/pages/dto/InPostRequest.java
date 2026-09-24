package com.pages.dto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InPostRequest {
    private String service;

    private InPostAddress sender;

    private InPostAddress receiver;

    private String customerReference;
}
