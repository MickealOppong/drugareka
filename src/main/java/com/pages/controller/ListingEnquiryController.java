package com.pages.controller;

import com.pages.service.ListingEnquiryService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/api/enquiry")
@RestController
public class ListingEnquiryController {

    private final ListingEnquiryService listingEnquiryService;


    public ListingEnquiryController(ListingEnquiryService listingEnquiryService) {
        this.listingEnquiryService = listingEnquiryService;
    }

    @PostMapping("/create")
    public boolean createEnquiry(@AuthenticationPrincipal Jwt jwt, Long listingId, String message){
       return listingEnquiryService.addBuyerEnquiry(jwt,listingId,message);
    }
}
