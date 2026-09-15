package com.pages.interfaces;

import com.pages.util.AdultValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = AdultValidator.class) // Links annotation to validation engine logic
@Documented
public @interface IsAdult {
    String message() default "You must be at least 18 years old to access this platform.";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
