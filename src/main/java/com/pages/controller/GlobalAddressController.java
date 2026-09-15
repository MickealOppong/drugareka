package com.pages.controller;

import com.pages.dto.AddressForm;
import com.pages.dto.AddressResponse;
import com.pages.dto.ResponseDto;
import com.pages.service.AppUserDetailsService;
import com.pages.service.GlobalAddressService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/address")
public class GlobalAddressController {

    private final GlobalAddressService globalAddressService;

    public GlobalAddressController(GlobalAddressService globalAddressService) {
        this.globalAddressService = globalAddressService;
    }

    @PostMapping("/new")
    public ResponseDto<Object> createAddress(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody AddressForm addressForm){
        log.info("{}",addressForm);
        return globalAddressService.EditOrAddAddress(jwt,addressForm);
    }
}
