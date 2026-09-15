package com.pages.util;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import java.math.BigDecimal;

@Configuration
@Data
public class ShippingProperties {

    @Value("${shipping.buyer-contribution-single}")
    private BigDecimal buyerContributionSingle;
    @Value("${shipping.buyer-contribution-group}")
    private BigDecimal buyerContributionGroup;

    //Calculates the seller's split gap dynamically in memory
    public BigDecimal getGroupFlatShippingCost() {
        return buyerContributionGroup;
    }

    //Calculates the seller's split gap dynamically in memory
    public BigDecimal getSingleFlatShippingCost() {
        return buyerContributionSingle;
    }
}
