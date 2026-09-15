package com.pages.service;

import com.pages.model.AppUser;
import com.pages.model.RefreshToken;
import com.pages.repository.AppUserRepo;
import com.pages.repository.RefreshTokenRepo;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Slf4j
@Service
public class RefreshTokenService {

    private final RefreshTokenRepo refreshTokenRepo;
    private final AppUserRepo appUserRepo;

    public RefreshTokenService(RefreshTokenRepo refreshTokenRepo, AppUserRepo appUserRepo) {
        this.refreshTokenRepo = refreshTokenRepo;
        this.appUserRepo = appUserRepo;
    }

    public RefreshToken createToken(AppUser appUser){
       RefreshToken refreshToken = RefreshToken.builder()
                .appUser(appUser)
                .expiredAt(Instant.now().plus(30, ChronoUnit.DAYS))
                .issuedAt(Instant.now())
                .refreshToken(UUID.randomUUID().toString())
                .build();
          return refreshTokenRepo.save(refreshToken);
    }


    @Transactional
    public RefreshToken newToken(String token,String username){
        //delete old token
        removeToken(token);
        //create new token
        RefreshToken refreshToken = RefreshToken.builder()
                .appUser(appUserRepo.findByUsername(username)
                        .orElseThrow(()->new UsernameNotFoundException("Could not find "+username)))
                .issuedAt(Instant.now())
                .expiredAt(Instant.now().plus(30, ChronoUnit.DAYS))
                .refreshToken(UUID.randomUUID().toString())
                .build();
        //return new token to user

        return refreshTokenRepo.save(refreshToken);
    }


    public boolean isTokenExpired(RefreshToken refreshToken){
        return refreshToken.getExpiredAt().isBefore(Instant.now());
    }

    public boolean removeToken(String refreshToken){
        RefreshToken token = refreshTokenRepo.findByRefreshToken(refreshToken).orElse(null);
        if(token!=null){
            refreshTokenRepo.delete(token);
            return true;
        }
        return false;
    }


}
