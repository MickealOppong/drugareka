package com.pages.controller;

import com.pages.dto.*;
import com.pages.service.*;

import jakarta.mail.Multipart;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;

@Slf4j
@RestController
@RequestMapping("/api/users")
public class AccountController {


    private final WishListService wishListService;
    private final AppUserDetailsService appUserDetailsService;
    private final ListingTransactionService listingTransactionService;
    private final SellerPayoutService sellerPayoutService;
    private final StripeConnectService stripeConnectService;

    public AccountController(WishListService wishListService, AppUserDetailsService appUserDetailsService, ListingTransactionService listingTransactionService, SellerPayoutService sellerPayoutService1, StripeConnectService stripeConnectService) {
        this.wishListService = wishListService;
        this.appUserDetailsService = appUserDetailsService;
        this.listingTransactionService = listingTransactionService;
        this.sellerPayoutService = sellerPayoutService1;
        this.stripeConnectService = stripeConnectService;
    }

    @PreAuthorize("hasAuthority('ROLE_USER')")
    @PostMapping("/add-wishlist")
    public ResponseDto<Object> toggleWishList(@AuthenticationPrincipal Jwt jwt, Long listingId){
        return wishListService.toggleWishList(listingId,jwt);
    }


    @PostMapping("/new")
    public ResponseDto<Object> createUser(@RequestBody @Valid UserRegistrationRequest request){
        return appUserDetailsService.addUser(request);
    }

    @GetMapping("/all")
    public List<UserDetailsDto> allUsers(){
        return appUserDetailsService.allUsers();
    }

    @GetMapping("/user")
    public ResponseDto<Object> getUser(@AuthenticationPrincipal Jwt jwt){
        return appUserDetailsService.getAppUser(jwt);
    }

    @GetMapping("/stripe-update")
    public ResponseDto<Object> isStripeVerified(@AuthenticationPrincipal Jwt jwt){
       return stripeConnectService.handleAccountUpdated(jwt);
    }
    @PutMapping(value = "/edit",consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseDto<Object> editUser(@AuthenticationPrincipal Jwt jwt,@ModelAttribute @Valid UserDetailsUpdateDto userDetailsDto){

        return appUserDetailsService.updateUser(jwt,userDetailsDto);
    }

    @GetMapping("/roles")
    public Set<String> allRoles(){
        return appUserDetailsService.getRoles();
    }

    @DeleteMapping("/delete")
    public ResponseDto<Object> deleteUser(Long userId){
      return  appUserDetailsService.deleteUserById(userId);
    }


    @GetMapping("/wishLists")
    public List<WishListResponse> wishList(@AuthenticationPrincipal Jwt jwt){
        return wishListService.allUserWishList(jwt);
    }

    @GetMapping("/recent-views")
    public List<ListTransResponse> myRecentViews(@RequestParam List<Long> ids, @AuthenticationPrincipal Jwt jwt){
        return listingTransactionService.getRecentViews(ids,jwt);
    }

    @GetMapping("/count")
    public ResponseEntity<Long> cartCount(@AuthenticationPrincipal Jwt jwt){
        return ResponseEntity.ok(wishListService.getWishlistCount(jwt));
    }

    @GetMapping("/payouts")
    public ListPagePayout myPayouts(@AuthenticationPrincipal Jwt jwt,int page,int size){
     return sellerPayoutService.payouts(jwt,page,size);
    }

    @PutMapping("/change-password")
    public ResponseDto<Object> changePassword(@AuthenticationPrincipal Jwt jwt,ChangePasswordDto dto){
        return appUserDetailsService.changePassword(jwt,dto);
    }



}
