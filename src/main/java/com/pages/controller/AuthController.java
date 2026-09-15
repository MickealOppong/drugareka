package com.pages.controller;

import com.pages.dto.*;
import com.pages.exception.InvalidOperationException;
import com.pages.model.AppUser;
import com.pages.model.AppUserRole;
import com.pages.model.RefreshToken;
import com.pages.service.AppUserDetailsService;
import com.pages.service.GlobalAddressService;
import com.pages.service.RefreshTokenService;
import com.pages.service.TokenService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/auth")
public class AuthController {


    private final AuthenticationManager authenticationManager;
    private final AppUserDetailsService userDetailsService;
    private final RefreshTokenService refreshTokenService;
    private final TokenService tokenService;



    public AuthController(AuthenticationManager authenticationManager, AppUserDetailsService userDetailsService,
                          RefreshTokenService refreshTokenService, TokenService tokenService) {
        this.authenticationManager = authenticationManager;
        this.userDetailsService = userDetailsService;
        this.refreshTokenService = refreshTokenService;
        this.tokenService = tokenService;
    }





    @PostMapping("/register")
    public ResponseDto<Object> createUser(@Valid @RequestBody UserRegistrationRequest userRegistrationRequest){


        if(userDetailsService.alreadyExist(userRegistrationRequest.getEmail())){
            throw new InvalidOperationException("Email already in use, choose a different email.");

        }
        if(!userRegistrationRequest.isTermsAccepted()){
           throw new InvalidOperationException("You must agree to terms and conditions.");

        }

        return  userDetailsService.add(userRegistrationRequest);

    }

    @PostMapping("/login")
    public ResponseDto<?> login(@RequestBody UserCredentials credentials){


        try{
            Authentication authenticationRequest = UsernamePasswordAuthenticationToken.unauthenticated(credentials.username(),credentials.password());
            Authentication authenticationResponse = this.authenticationManager.authenticate(authenticationRequest);



            if(authenticationResponse.isAuthenticated()){

                SecurityContextHolder.getContext().setAuthentication(authenticationResponse);

                AppUser appUser = userDetailsService.getAppUserByUsername(credentials.username());

                RefreshToken refreshToken =refreshTokenService.createToken(appUser);


                TokenDto tokenDto = TokenDto.builder()
                        .token(tokenService.token(authenticationResponse).orElse(null))
                        .refreshToken(refreshToken.getRefreshToken())
                        .expiredAt(refreshToken.getExpiredAt())
                        .issuedAt(refreshToken.getIssuedAt())
                        .build();
                LoginResponse userDto = LoginResponse.builder()
                        .tokenDto(tokenDto)
                        .email(appUser.getUsername())
                        .userId(appUser.getId())
                        .roles(appUser.getUserRoles().stream().map(AppUserRole::getRole).collect(Collectors.toSet()))
                        .firstName(appUser.getFirstName())
                        .lastName(appUser.getLastName())
                        .build();

                return ResponseDto.builder()
                        .data(userDto)
                        .message("Success")
                        .httpStatus(HttpStatus.OK.value())
                        .build();
            }
            return ResponseDto.builder()
                    .data(null)
                    .message("Could not authenticate username or password")
                    .httpStatus(HttpStatus.UNAUTHORIZED.value())
                    .build();

        }catch (UsernameNotFoundException ex) {
            return ResponseDto.builder()
                    .data(null)
                    .message("Username does not exist.")
                    .httpStatus(HttpStatus.UNAUTHORIZED.value())
                    .build();

        } catch (BadCredentialsException ex) {
            return ResponseDto.builder()
                    .data(null)
                    .message("Incorrect password.")
                    .httpStatus(HttpStatus.UNAUTHORIZED.value())
                    .build();

        } catch (Exception ex) {
            return ResponseDto.builder()
                    .data(null)
                    .message(ex.getMessage())
                    .httpStatus(HttpStatus.UNAUTHORIZED.value())
                    .build();
        }

    }

    @DeleteMapping("/logout")
    public ResponseEntity<Boolean> logoutUser(String refreshToken){
        try{
            return ResponseEntity.ok(refreshTokenService.removeToken(refreshToken));
        }catch (Exception  e){
            return ResponseEntity.badRequest().body(false);
        }
    }


}

