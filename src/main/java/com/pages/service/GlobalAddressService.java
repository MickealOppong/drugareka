package com.pages.service;

import com.pages.dto.AddressForm;
import com.pages.dto.AddressResponse;
import com.pages.dto.ResponseDto;
import com.pages.model.AppUser;
import com.pages.repository.GlobalAddressRepo;
import com.pages.util.GlobalAddress;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
public class GlobalAddressService {

    private final GlobalAddressRepo globalAddressRepo;
    private final AppUserDetailsService appUserDetailsService;

    public GlobalAddressService(GlobalAddressRepo globalAddressRepo, AppUserDetailsService appUserDetailsService) {
        this.globalAddressRepo = globalAddressRepo;
        this.appUserDetailsService = appUserDetailsService;
    }

    public void addAddress(Jwt jwt, AddressForm dto){
        String username = jwt.getSubject();
        AppUser buyer = appUserDetailsService.getAppUserByUsername(username);

        GlobalAddress globalAddress = GlobalAddress.builder()
                .country(dto.getCountry())
                .city(dto.getCity())
                .postCode(dto.getPostalCode())
                .street(dto.getStreet())
                .contact(dto.getContact())
                .appUser(buyer)
                .build();
        globalAddressRepo.save(globalAddress);
    }

    public void addAddress(AppUser buyer, AddressForm dto){
        log.info("{} calling",buyer.getId());
        GlobalAddress globalAddress = GlobalAddress.builder()
                .country(dto.getCountry())
                .city(dto.getCity())
                .postCode(dto.getPostalCode())
                .street(dto.getStreet())
                .appUser(buyer)
                .contact(dto.getContact())
                .build();
        globalAddressRepo.save(globalAddress);
    }

    public ResponseDto<Object> EditOrAddAddress(Jwt jwt, AddressForm dto){

       try{
           String username = jwt.getSubject();
           AppUser buyer = appUserDetailsService.getAppUserByUsername(username);

           //find address if exists
           GlobalAddress currentAddress =globalAddressRepo.findByAppUserId(buyer.getId()).orElse(null);
log.info("{}",currentAddress);
           if(currentAddress ==null){
               addAddress(buyer,dto);
               return ResponseDto.builder()
                       .message("Address created")
                       .httpStatus(HttpStatus.OK.value())
                       .build();
           }else{
               currentAddress.setCity(dto.getCity());
               currentAddress.setStreet(dto.getStreet());
               currentAddress.setPostCode(dto.getPostalCode());
               currentAddress.setContact(dto.getContact());
               globalAddressRepo.save(currentAddress);
               return ResponseDto.builder()
                       .message("Address updated")
                       .httpStatus(HttpStatus.OK.value())
                       .build();
           }
       }catch (Exception e){
           return ResponseDto.builder()
                   .message(e.getMessage())
                   .httpStatus(HttpStatus.BAD_REQUEST.value())
                   .build();
       }

    }

    public AddressResponse getAddress(Jwt jwt){
        //current user
        String username = jwt.getSubject();
        AppUser currentUser = appUserDetailsService.getAppUserByUsername(username);

        //address
        Optional<AddressResponse> addressResponse = globalAddressRepo.findByAppUserId(currentUser.getId()).map(GlobalAddress::toAddressResponse);
        return addressResponse.orElse(null);
    }

    public AddressResponse getAddress(AppUser appUser){

        //address
        Optional<AddressResponse> addressResponse = globalAddressRepo.findByAppUserId(appUser.getId()).map(GlobalAddress::toAddressResponse);
        return addressResponse.orElse(null);
    }

}
