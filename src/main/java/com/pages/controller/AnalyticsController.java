package com.pages.controller;

import com.pages.dto.AnalyticsDto;
import com.pages.service.AnalyticsService;
import com.pages.service.AppUserDetailsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {



    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/dashboard")
    public AnalyticsDto analytics(@AuthenticationPrincipal Jwt jwt){
       return analyticsService.dashboardAnalytics(jwt);
    }

}
