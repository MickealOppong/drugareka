package com.pages.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AddressForm {

    @NotBlank(message = "Miejscowość nie może być pusta")
    private String city;

    @NotBlank(message = "Ulica i numer domu nie mogą być puste")
    private String street;

    // Optional field - tracking parameters can remain standard string values
    private String country;

    @NotBlank(message = "Kod pocztowy nie może być pusty")
    @Pattern(
            regexp = "\\d{2}-\\d{3}",
            message = "Nieprawidłowy format kodu pocztowego. Wymagany format to XX-XXX (np. 97-300)"
    )
    private String postalCode;

    @NotBlank(message = "Numer telefonu nie może być pusty")
    private String contact;


}
