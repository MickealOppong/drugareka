package com.pages.util;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import java.math.BigDecimal;
import java.math.MathContext;

@Configuration
@Data
public class ShippingProperties {

    @Value("${shipping.buyer-contribution-single}")
    private BigDecimal buyerContributionSingle;
    @Value("${shipping.buyer-contribution-group}")
    private BigDecimal buyerContributionGroup;
    @Value("${shipping.buyer-contribution-heavy}")
    private BigDecimal buyerContributionHeavyItem;

    //Calculates the seller's split gap dynamically in memory
    public BigDecimal getGroupFlatShippingCost(String categorySlug) {

        if(categorySlug!=null && categorySlug.equalsIgnoreCase("machines-spare-parts")){
            return buyerContributionGroup;
        }
        return buyerContributionGroup;
    }

    //Calculates the seller's split gap dynamically in memory
    public BigDecimal getSingleFlatShippingCost(String categorySlug) {
            if(categorySlug!=null && categorySlug.equalsIgnoreCase("machines-spare-parts")){
                return buyerContributionGroup;
            }
        return buyerContributionSingle;
    }
}
