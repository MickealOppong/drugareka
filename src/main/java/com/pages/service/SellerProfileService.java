package com.pages.service;

import com.pages.dto.ResponseDto;
import com.pages.dto.SellerProfileDto;
import com.pages.model.SellerPayout;
import com.pages.model.SellerProfile;
import com.pages.repository.SellerProfileRepo;
import org.springframework.stereotype.Service;

@Service
public class SellerProfileService {

    private final SellerProfileRepo sellerProfileRepo;

    public SellerProfileService(SellerProfileRepo sellerProfileRepo) {
        this.sellerProfileRepo = sellerProfileRepo;
    }

    public SellerProfile getSeller(Long id){
       return sellerProfileRepo.findById(id).orElse(null);
    }

    public SellerProfile findOrCreateSellerProfile(SellerProfileDto dto){
              return    sellerProfileRepo.findByUserId(dto.getUser().getId())
                         .orElseGet(()->{
                     SellerProfile profile = new SellerProfile(dto);
                     return sellerProfileRepo.save(profile);
                 });
    }

    public SellerProfile getSellerProfile(Long userid){
        return sellerProfileRepo.findByUserId(userid).orElse(null);
    }
}
