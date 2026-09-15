package com.pages.dto;

import com.pages.interfaces.IsAdult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserRegistrationRequest {

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


  @NotNull(message = "Password cannot be null")
  @Size(min = 8, max = 32, message = "Password must be between 8 and 32 characters long")
  @Pattern(
          regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*#?&])[A-Za-z\\d@$!%*#?&]{8,}$",
          message = "Password must contain at least one uppercase letter, one lowercase letter, one number, and one special character"
  )
    @ToString.Exclude
    private String password;

    @NotNull(message = "You must provide a value for terms acceptance")
    @AssertTrue(message = "You must accept the Terms and Conditions to proceed")
    private boolean isTermsAccepted;

    private String roles;

}
