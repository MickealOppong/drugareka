package com.pages.dto;

import com.pages.interfaces.IsAdult;
import jakarta.persistence.Column;
import jakarta.validation.constraints.*;
import lombok.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Getter
@Setter
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class UserDetailsUpdateDto {

    private Long id;
    @NotNull(message = "first name cannot be null")
    @NotBlank(message = "First name cannot be empty")
    private String firstName;

    @NotNull(message = "Last name cannot be empty")
    @NotBlank(message = "Last name cannot be empty")
    private String lastName;


    private String accountNumber;

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
