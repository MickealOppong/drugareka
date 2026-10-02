package com.pages.service;

import com.pages.dto.ResponseDto;
import com.pages.dto.SellerProfileDto;
import com.pages.exception.EntityNotFoundException;
import com.pages.exception.InvalidOperationException;
import com.pages.model.*;
import com.pages.repository.SellerPayoutRepo;
import com.pages.repository.SellerProfileRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class SellerProfileService {

    private final SellerProfileRepo sellerProfileRepo;
    private final AppUserDetailsService appUserDetailsService;
    private final SellerPayoutRepo sellerPayoutRepo;

    public SellerProfileService(SellerProfileRepo sellerProfileRepo, AppUserDetailsService appUserDetailsService, SellerPayoutRepo sellerPayoutRepo) {
        this.sellerProfileRepo = sellerProfileRepo;
        this.appUserDetailsService = appUserDetailsService;
        this.sellerPayoutRepo = sellerPayoutRepo;
    }

    public SellerProfile getSeller(Long id){
       return sellerProfileRepo.findById(id).orElse(null);
    }

    public SellerProfile findOrCreateSellerProfile(SellerProfileDto dto){
              return    sellerProfileRepo.findByUser(dto.getUser())
                         .orElseGet(()->{
                     SellerProfile profile = new SellerProfile(dto);
                     return sellerProfileRepo.save(profile);
                 });
    }

    public SellerProfile getSellerProfile(Long userId){
        return sellerProfileRepo.findByUserId(userId).orElse(null);
    }


    public SellerProfile updateSellerTotalSales(List<ListingOrderItem> orderItems){

        try{
            for(ListingOrderItem item :orderItems){


                SellerProfile seller=  sellerProfileRepo.findById(item.getSeller().getId()).orElse(null);

                if(seller !=null){
                    log.info("Updating seller:{}",seller.getUser().getUsername());

                    BigDecimal totalSales =seller.getTotalSales()!=null?seller.getTotalSales():BigDecimal.ZERO;

                    BigDecimal orderTotal = item.getSellerPrice().add(item.getShippingCost());
                    seller.setTotalSales(totalSales.add(orderTotal));

                  return sellerProfileRepo.save(seller);
                }
                return null;
            }
            return null;
        }catch (Exception e){
            throw new InvalidOperationException("Could not update seller profile");
        }

    }

    public void updateSellerTotalPayoutWithRefund(ListingOrderItem orderItem){
        sellerProfileRepo.findById(orderItem.getSeller().getId()).ifPresent(seller -> {

            BigDecimal totalSales =seller.getTotalSales()!=null?seller.getTotalSales():BigDecimal.ZERO;

            BigDecimal refundAmount = orderItem.getSellerPrice().add(orderItem.getShippingCost());

            seller.setTotalSales(totalSales.subtract(refundAmount));

            sellerProfileRepo.save(seller);
        });
    }


    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public BigDecimal outStandingPayout(Jwt jwt){
        if(jwt!=null){
            AppUser appUser = appUserDetailsService.getAppUserByUsername(jwt.getSubject());

            SellerProfile sellerProfile = sellerProfileRepo.findByUser(appUser).orElse(null);

            if(sellerProfile!=null){
                BigDecimal settlements = sellerProfile.getTotalSettlement()!=null?sellerProfile.getTotalSettlement():BigDecimal.ZERO;
              BigDecimal sales = sellerProfile.getTotalSales()!=null?sellerProfile.getTotalSales():BigDecimal.ZERO;

               return sales.subtract(settlements);
            }
            return BigDecimal.ZERO;
        }
        return BigDecimal.ZERO;
    }
}
