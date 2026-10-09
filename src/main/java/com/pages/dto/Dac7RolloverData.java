package com.pages.dto;

import java.math.BigDecimal;

public record Dac7RolloverData(
        String email,
        String firstName,
        int salesCount,
        BigDecimal grossVolume
) {
}
