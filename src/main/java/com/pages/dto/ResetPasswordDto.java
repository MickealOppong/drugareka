package com.pages.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ResetPasswordDto {

    private String username;
    private String location;
    private LocalDate date_of_birth;
    private String newPassword;
    private String confirmNewPassword;
}
