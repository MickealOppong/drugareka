package com.pages.controller;

import com.pages.dto.ListPageSellerProfile;
import com.pages.dto.SellerProfileUpdate;
import com.pages.model.SellerProfile;
import com.pages.service.SellerProfileService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RequestMapping("/api/sellerProfile")
@RestController
public class SellerProfileController {

    private final SellerProfileService sellerProfileService;

    public SellerProfileController(SellerProfileService sellerProfileService) {
        this.sellerProfileService = sellerProfileService;
    }

    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @GetMapping("/sellers")
    public ListPageSellerProfile all(@RequestParam String query,@RequestParam int page,@RequestParam int size){
        return sellerProfileService.getSellers(query,page,size);
    }

    @PutMapping("/update-status")
    public boolean updateSellerProfileStatus(@ModelAttribute SellerProfileUpdate dto){
        return sellerProfileService.changeStatus(dto);
    }
}
