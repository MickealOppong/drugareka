package com.pages.repository;

import com.pages.util.Dac7AnnualSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface Dac7AnnualSummaryRepo extends JpaRepository<Dac7AnnualSummary,Long> {
    Optional<Dac7AnnualSummary> findBySellerProfileIdAndTaxYear(
            Long sellerProfileId,
            Integer taxYear
    );

    boolean existsBySellerProfileIdAndTaxYear(
            Long sellerProfileId,
            Integer taxYear
    );
}
