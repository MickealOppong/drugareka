package com.pages.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class PricingService {


    static BigDecimal marginPercentage = BigDecimal.valueOf(15);

    public BigDecimal calculateStorePrice(BigDecimal sellerPrice) {
        if (sellerPrice == null || sellerPrice.signum() < 0) {
            throw new IllegalArgumentException("Invalid seller price");
        }

        if (marginPercentage == null || marginPercentage.signum() < 0) {
            throw new IllegalArgumentException("Invalid margin");
        }

        return sellerPrice
                .multiply(
                        BigDecimal.ONE.add(
                                marginPercentage.divide(
                                        BigDecimal.valueOf(100),
                                        4,
                                        RoundingMode.HALF_UP
                                )
                        )
                )
                .setScale(2, RoundingMode.HALF_UP);
    }
}
