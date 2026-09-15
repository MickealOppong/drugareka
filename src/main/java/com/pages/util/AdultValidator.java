package com.pages.util;

import com.pages.interfaces.IsAdult;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.time.LocalDate;
import java.time.Period;

public class AdultValidator implements ConstraintValidator<IsAdult, LocalDate> {

    @Override
    public boolean isValid(LocalDate birthDate, ConstraintValidatorContext context) {
        // Let @NotNull handle missing inputs separately
        if (birthDate == null) {
            return true;
        }

        // Calculate the exact years between birthdate and today's execution window
        return Period.between(birthDate, LocalDate.now()).getYears() >= 18;
    }
}
