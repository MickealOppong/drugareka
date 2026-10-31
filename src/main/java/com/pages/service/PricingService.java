
package com.pages.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Slf4j
@Service
public class PricingService {

    private static final int MONEY_SCALE = 2;

    private BigDecimal vatRate;
    @Value("${kasoa.pricing.checkout-threshold}")
    private BigDecimal checkoutThreshold;
    @Value("${kasoa.pricing.margin-percentage}")
    private BigDecimal percentageRate;
    @Value("${kasoa.pricing.flat-fee}")
    private  BigDecimal flatFee;


    public BigDecimal getServiceCharge(BigDecimal totalSales) {
        validateAmount(totalSales);

        if (totalSales.compareTo(checkoutThreshold) >= 0) {
            return totalSales
                    .multiply(percentageRate)
                    .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        }

        return flatFee.setScale(
                MONEY_SCALE,
                RoundingMode.HALF_UP
        );
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null
                || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "Total sales must be greater than zero."
            );
        }
    }

    private void validateConfiguration() {
        if (checkoutThreshold == null
                || checkoutThreshold.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException(
                    "Checkout threshold must be greater than zero."
            );
        }

        if (percentageRate == null
                || percentageRate.compareTo(BigDecimal.ZERO) < 0
                || percentageRate.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalStateException(
                    "Percentage rate must be between 0 and 1."
            );
        }

        if (flatFee == null
                || flatFee.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalStateException(
                    "Flat fee cannot be negative."
            );
        }
    }
}
