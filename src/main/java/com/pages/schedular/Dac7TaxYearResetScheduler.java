package com.pages.schedular;

import com.pages.dto.Dac7RolloverData;
import com.pages.model.SellerProfile;
import com.pages.repository.Dac7AnnualSummaryRepo;
import com.pages.repository.SellerProfileRepo;
import com.pages.service.EmailNotificationService;
import com.pages.util.Dac7AnnualSummary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class Dac7TaxYearResetScheduler {

    @Value("${dac7.admin.notification}")
    private String adminEmail;

    private final SellerProfileRepo sellerProfileRepo;
    private final EmailNotificationService emailNotificationService;
    private final Dac7AnnualSummaryRepo dac7AnnualSummaryRepo;

    Instant instant = Instant.now();

    int currentYear = instant
            .atZone(ZoneId.of("Europe/Warsaw"))
            .getYear();

    int previousYear = currentYear-1;

    /**
     *  ANNUAL MANDATORY TAX CALENDAR RESET CRON TASK
     * Pattern: "0 0 0 1 1 *"
     * Fires exactly at 00:00:00 (Midnight) on January 1st every calendar year.
     */
    @Scheduled(cron = "0 0 0 1 1 *")
    @Transactional
    public void executeAnnualDac7TaxYearReset() {

        log.warn(
                "Tax Engine Alert: Midnight January 1st reached. " +
                        "Initializing global DAC7 annual metrics reset..."
        );

        try {

            // ============================================================
            // 1. Load sellers and archive previous year's data
            // ============================================================

            List<SellerProfile> sellers = sellerProfileRepo.findAll();

            List<Dac7RolloverData> rolloverData = sellers.stream()
                    .map(seller -> {

                        int salesCount = seller.getCurrentYearSalesCount();

                        BigDecimal grossVolume =
                                seller.getCurrentYearGrossVolumePln();

                        Dac7AnnualSummary summary =
                                Dac7AnnualSummary.builder()
                                        .sellerProfile(seller)
                                        .taxYear(previousYear)
                                        .salesCount(salesCount)
                                        .grossVolume(grossVolume)
                                        .build();

                        dac7AnnualSummaryRepo.save(summary);

                        return new Dac7RolloverData(
                                seller.getUser().getUsername(),
                                seller.getUser().getFirstName(),
                                salesCount,
                                grossVolume
                        );
                    })
                    .toList();


            // ============================================================
            // 2. Reset current-year counters
            // ============================================================

            int modifiedRowsCount =
                    sellerProfileRepo.resetAnnualDac7TaxCountersBulk();


            log.info(
                    "Tax Engine Success: Tax year rollover complete. " +
                            "Archived {} seller profiles and reset {} " +
                            "seller profile tax counters to zero.",
                    rolloverData.size(),
                    modifiedRowsCount
            );


            // ============================================================
            // 3. Send seller notifications
            // ============================================================

            rolloverData.forEach(data ->
                    emailNotificationService.sendDac7RolloverUpdate(
                            data.email(),
                            data.firstName(),
                            data.salesCount(),
                            data.grossVolume(),
                            currentYear
                    )
            );


            // ============================================================
            // 4. Notify administrator
            // ============================================================

            emailNotificationService.sendDac7AnnualResetAdminEmail(
                    adminEmail,
                    modifiedRowsCount,
                    currentYear
            );

        } catch (Exception e) {

            log.error(
                    "Tax Engine CRITICAL FAILURE: " +
                            "Failed to execute annual DAC7 calendar-year data reset!",
                    e
            );

            emailNotificationService.sendDac7AnnualResetFailureAdminEmail(
                    adminEmail,
                    currentYear,
                    e
            );

            throw e;
        }
    }
}
