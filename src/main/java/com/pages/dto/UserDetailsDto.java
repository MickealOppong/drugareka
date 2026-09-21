package com.pages.dto;

import com.pages.model.AppUserRole;
import com.pages.util.GlobalAddress;
import jakarta.persistence.Column;
import lombok.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserDetailsDto {

    private Long userId;
    private String firstName;
    private String lastName;
    private String email;
    private Set<String> roles;
    private AddressResponse address;
    private String accountNumber;




}
