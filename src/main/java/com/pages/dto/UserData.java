package com.pages.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserData{

    @NotNull(message = "first name cannot be null")
    @NotBlank(message = "First name cannot be empty")
    private String firstName;

    @NotNull(message = "Last name cannot be empty")
    @NotBlank(message = "Last name cannot be empty")
    private String lastName;

    @NotNull(message = "Email cannot be empty")
    @NotBlank(message = "Email cannot be empty")
    private String email;

    private String dob;
    private boolean active;
    @NotNull(message = "Role cannot be empty")
    @NotBlank(message = "Role name cannot be empty")
    private String role;

}