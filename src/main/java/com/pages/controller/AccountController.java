package com.pages.controller;

import com.pages.dto.*;
import com.pages.service.*;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@Slf4j
@RestController
@RequestMapping("/api/users")
public class AccountController {


    private final WishListService wishListService;
    private final AppUserDetailsService appUserDetailsService;
    private final ListingTransactionService listingTransactionService;
    private final ListingOrderService listingOrderService;
    private final SellerPayoutService sellerPayoutService;

    public AccountController(WishListService wishListService, AppUserDetailsService appUserDetailsService, ListingTransactionService listingTransactionService, ListingOrderService listingOrderService, SellerPayoutService sellerPayoutService, SellerPayoutService sellerPayoutService1) {
        this.wishListService = wishListService;
        this.appUserDetailsService = appUserDetailsService;
        this.listingTransactionService = listingTransactionService;
        this.listingOrderService = listingOrderService;
        this.sellerPayoutService = sellerPayoutService1;
    }

    @PreAuthorize("hasAuthority('ROLE_USER')")
    @PostMapping("/add-wishlist")
    public ResponseDto<Object> toggleWishList(@AuthenticationPrincipal Jwt jwt, Long listingId){
        return wishListService.toggleWishList(listingId,jwt);
    }


    @PostMapping("/new")
    public ResponseDto<Object> createUser(@RequestBody @Valid UserDetailsUpdateDto request){
        return appUserDetailsService.addUser(request);
    }

    @GetMapping("/all")
    public List<UserDetailsDto> allUsers(){
        return appUserDetailsService.allUsers();
    }

    @GetMapping("/user")
    public ResponseDto<Object> getUser(String username){
        return appUserDetailsService.getAppUser(username);
    }

    @PutMapping("/edit")
    public ResponseDto<Object> editUser(@RequestBody @Valid UserDetailsUpdateDto userDetailsDto){
        return appUserDetailsService.updateUser(userDetailsDto);
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
    public List<SellerPayoutResponse> myPayouts(@AuthenticationPrincipal Jwt jwt){
     return sellerPayoutService.payouts(jwt);
    }

}
