package com.pages.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Builder
@Data
public class TokenDto {
    private Instant expiredAt;
    private Instant issuedAt;
    private String refreshToken;
    private String token;
}