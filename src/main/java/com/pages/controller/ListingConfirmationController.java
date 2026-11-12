package com.pages.controller;

import com.pages.service.ListingConfirmationService;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/listings/availability")

public class ListingConfirmationController {

    private final ListingConfirmationService availabilityService;

    public ListingConfirmationController(ListingConfirmationService availabilityService) {
        this.availabilityService = availabilityService;
    }

    @PutMapping("/confirm")
    public ResponseEntity<Boolean> confirmAvailability(
            @RequestParam String token) {

       return ResponseEntity.ok(availabilityService.confirm(token));

    }

    @PutMapping("/sold")
    public ResponseEntity<Boolean> markAsSold(
            @RequestParam String token) {

      return   ResponseEntity.ok(availabilityService.markAsSold(token));

    }

}
