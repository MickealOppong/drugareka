package com.pages.service;

import com.pages.model.ListingOrder;
import com.pages.model.ListingOrderItem;
import com.pages.model.ServiceCharge;
import com.pages.repository.ServiceChargeRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

@Slf4j
@Service
public class ServiceChargeService {

    @Value("${service.charge.vat-rate}")
    private BigDecimal vatRate;

    private final ServiceChargeRepo serviceChargeRepo;


    public ServiceChargeService(ServiceChargeRepo serviceChargeRepo) {
        this.serviceChargeRepo = serviceChargeRepo;
    }

    public void createCharge(ListingOrder listingOrder) {

        // Step 1: Declare your standard Polish 23% VAT extraction parameters as explicit BigDecimals
        BigDecimal vatNumerator = vatRate;
        BigDecimal vatDenominator = new BigDecimal(100).add(vatNumerator);

// Step 2: Calculate the tax fraction multiplier using 6 places of precision to prevent rounding leak errors
        BigDecimal vatMultiplier = vatNumerator.divide(vatDenominator, 6, RoundingMode.HALF_UP);

// Step 3: Extract the exact VAT component safely from your Order Total Gross Price
        BigDecimal grossServiceChargeTotal = listingOrder.getItems().stream()
                .map(ListingOrderItem::getServiceCharge)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        log.info("Billing Engine: Summed up overall gross service fee totals: {} zł for Order ID: {}",
                grossServiceChargeTotal, listingOrder.getId());

        BigDecimal vatAmount = grossServiceChargeTotal.multiply(vatMultiplier)
                .setScale(2, RoundingMode.HALF_UP);

        log.info("Tax Engine: Extracted 23% VAT accounting component: {} zł from Gross Order Total: {} zł",
                vatAmount, grossServiceChargeTotal);


        ServiceCharge serviceCharge = ServiceCharge.builder()
                .order(listingOrder)
                .buyer(listingOrder.getBuyer())
                .currency(listingOrder.getCurrency())
                .description("Service charge earned")
                .grossAmount(grossServiceChargeTotal)
                .netAmount(grossServiceChargeTotal.subtract(vatAmount))
                .vatAmount(vatAmount)
                .build();
        serviceChargeRepo.save(serviceCharge);
        log.info("Tax Engine: Service Charge ledger block successfully locked down for Order ID: {}", listingOrder.getId());
    }
}
