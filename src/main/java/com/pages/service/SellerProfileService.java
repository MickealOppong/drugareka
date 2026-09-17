package com.pages.service;

import com.pages.dto.ResponseDto;
import com.pages.dto.SellerProfileDto;
import com.pages.model.*;
import com.pages.repository.SellerProfileRepo;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class SellerProfileService {

    private final SellerProfileRepo sellerProfileRepo;
    private final AppUserDetailsService appUserDetailsService;

    public SellerProfileService(SellerProfileRepo sellerProfileRepo, AppUserDetailsService appUserDetailsService) {
        this.sellerProfileRepo = sellerProfileRepo;
        this.appUserDetailsService = appUserDetailsService;
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


    public void updateSellerTotalPayout(List<ListingOrderItem> orderItems){
       for(ListingOrderItem item :orderItems){

           sellerProfileRepo.findById(item.getSeller().getId()).ifPresent(seller -> {

               BigDecimal totalSales =seller.getTotalSales()!=null?seller.getTotalSales():BigDecimal.ZERO;

                       seller.setTotalSales(totalSales.add(item.getFinalizedPrice()));

               sellerProfileRepo.save(seller);
           });
       }
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public BigDecimal outStandingPayout(Jwt jwt){
        if(jwt!=null){
            AppUser appUser = appUserDetailsService.getAppUserByUsername(jwt.getSubject());

            SellerProfile sellerProfile = sellerProfileRepo.findByUserId(appUser.getId()).orElse(null);
            if(sellerProfile!=null){
               return sellerProfile.getTotalSales();
            }
            return BigDecimal.ZERO;
        }
        return BigDecimal.ZERO;
    }
}
