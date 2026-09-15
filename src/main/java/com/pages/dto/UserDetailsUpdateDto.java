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

    private Long userId;
    @NotNull(message = "first name cannot be null")
    @NotBlank(message = "First name cannot be empty")
    private String firstName;

    @NotNull(message = "Last name cannot be empty")
    @NotBlank(message = "Last name cannot be empty")
    private String lastName;

    @NotNull(message = "Username/Email cannot be null")
    @NotBlank(message = "Username/Email cannot be empty")
    @Pattern(
            regexp = "^[a-zA-Z0-9_!#$%&'*+/=?`{|}~^-]+(?:\\.[a-zA-Z0-9_!#$%&'*+/=?`{|}~^-]+)*@[a-zA-Z0-9-]+(?:\\.[a-zA-Z0-9-]+)*$",
            message = "Please provide a valid email address as your username"
    )
    private String email;

    @NotNull(message = "Date of birth cannot be null")
    @IsAdult
    @Past(message = "Birth date must be a past date")
    private LocalDate dob;

    @ToString.Exclude
    private String password;

    @NotNull(message = "You must provide a value for terms acceptance")
    @AssertTrue(message = "You must accept the Terms and Conditions to proceed")
    private boolean isTermsAccepted;

    @NotNull(message = "Role cannot be null")
    @NotBlank(message = "Role name cannot be empty")
    private String role;
}
