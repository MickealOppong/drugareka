package com.pages.controller;

import com.pages.dto.ListingUpdateResponse;
import com.pages.dto.ProductData;
import com.pages.service.ListingEnquiryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@Slf4j
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

    @PutMapping(value = "/listing/edit",consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public boolean validateAndEditListing(@ModelAttribute ProductData productData){
        return listingEnquiryService.updateListingWithToken(productData.getToken(),productData);
    }

    @PostMapping("/enquiry-token")
    public ListingUpdateResponse validateEnquiryToken(String token){
        return listingEnquiryService.validateToken(token);
    }
}
