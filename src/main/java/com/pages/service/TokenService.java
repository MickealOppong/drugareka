package com.pages.service;

import com.pages.model.AppUser;
import com.pages.repository.AppUserRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;


@Slf4j
@Service
public class TokenService {


    private final JwtEncoder jwtEncoder;
    private final AppUserRepo appUserRepo;
    //private final RefreshTokenService refreshTokenService;


    public TokenService(JwtEncoder jwtEncoder, AppUserRepo appUserRepo, RefreshTokenService refreshTokenService) {
        this.jwtEncoder = jwtEncoder;
        this.appUserRepo = appUserRepo;
       // this.refreshTokenService = refreshTokenService;
    }

    public Optional<String> token(Authentication authentication){


        AppUser appUser =appUserRepo.findByUsername(authentication.getName()).orElse(null);
        if(appUser!=null){
            Set<String> roles = AuthorityUtils.authorityListToSet(appUser.getAuthorities())
                    .stream()
                    .collect(Collectors.collectingAndThen(Collectors.toSet(), Collections::unmodifiableSet));
            JwtClaimsSet claimsSet = JwtClaimsSet.builder()
                    .issuer("local")
                    .issuedAt(Instant.now())
                    .expiresAt(Instant.now().plus(30, ChronoUnit.DAYS))
                    .subject(authentication.getName())
                    .claim("ROLE",roles)
                    .build();
            return Optional.of(jwtEncoder.encode(JwtEncoderParameters.from(claimsSet)).getTokenValue());
        }
        return Optional.empty();
    }

    public Optional<String> token(String username){


        AppUser appUser =appUserRepo.findByUsername(username).orElse(null);

        if(appUser!=null){
            Set<String> roles = AuthorityUtils.authorityListToSet(appUser.getAuthorities())
                    .stream()
                    .collect(Collectors.collectingAndThen(Collectors.toSet(), Collections::unmodifiableSet));
            JwtClaimsSet claimsSet = JwtClaimsSet.builder()
                    .issuer("local")
                    .issuedAt(Instant.now())
                    .expiresAt(Instant.now().plus(30, ChronoUnit.DAYS))
                    .subject(appUser.getUsername())
                    .claim("ROLE",roles)
                    .build();
            return Optional.of(jwtEncoder.encode(JwtEncoderParameters.from(claimsSet)).getTokenValue());
        }
        return Optional.empty();
    }
}

