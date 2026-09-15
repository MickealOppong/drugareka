package com.pages.interfaces;

import com.pages.util.MediaFileValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = MediaFileValidator.class)
public @interface ValidMedia {

    String message() default "Invalid media file";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}