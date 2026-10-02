package com.pages.controller;

import com.pages.dto.CourierResponse;
import com.pages.dto.ShippingOptionRequest;
import com.pages.service.ShippingOptionService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/courier")
public class ShippingOptionController {


    private final ShippingOptionService shippingOptionService;

    public ShippingOptionController(ShippingOptionService shippingOptionService) {
        this.shippingOptionService = shippingOptionService;
    }

    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @GetMapping("/options")
    public List<CourierResponse> getAll(@AuthenticationPrincipal Jwt jwt){
        return shippingOptionService.getCourierList(jwt);
    }

    @PostMapping("/courier-charge")
    public void addCourier(@AuthenticationPrincipal Jwt jwt,@RequestBody @Valid ShippingOptionRequest request){
        shippingOptionService.addOrEditShippingOption(jwt,request);
    }
}
