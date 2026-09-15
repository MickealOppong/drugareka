package com.pages.dto;


import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Set;

@Builder
@Data
public class LoginResponse {

    private Long userId;
    private String firstName;
    private String lastName;
    private String email;
    private TokenDto tokenDto;
    private Set<String> roles;

}

