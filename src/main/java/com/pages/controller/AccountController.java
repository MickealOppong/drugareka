package com.pages.controller;

import com.pages.dto.*;
import com.pages.service.*;

import com.stripe.exception.StripeException;
import jakarta.mail.Multipart;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;

@Slf4j
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class AccountController {


    private final WishListService wishListService;
    private final AppUserDetailsService appUserDetailsService;
    private final ListingTransactionService listingTransactionService;
    private final SellerPayoutService sellerPayoutService;
    private final StripeConnectService stripeConnectService;
    private final SellerProfileService sellerProfileService;



    @PreAuthorize("hasAuthority('ROLE_USER')")
    @PostMapping("/add-wishlist")
    public ResponseDto<Object> toggleWishList(@AuthenticationPrincipal Jwt jwt, Long listingId){
        return wishListService.toggleWishList(listingId,jwt);
    }

    @GetMapping("/stripe/dashboard")
    public ResponseDto<Object> openStripeDashboard(
            @AuthenticationPrincipal Jwt jwt) throws StripeException {
        // SellerProfile and its Stripe connected account ID.
        String stripeAccountId =
               sellerProfileService.getStripeAccountId(jwt);

        String url = stripeConnectService.createStripeDashboardLink(
                stripeAccountId
        );

        return ResponseDto.builder()
                .httpStatus(HttpStatus.FOUND.value())
                .data(URI.create(url))
                .build();
    }


    @PostMapping("/new")
    public ResponseDto<Object> createUser(@RequestBody @Valid UserRegistrationRequest request){
        return appUserDetailsService.addUser(request);
    }

    @GetMapping("/all")
    public ListPageUsers allUsers(int page,int size){
        return appUserDetailsService.allUsers(page,size);
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
    public ResponseDto<Object> changePassword(@AuthenticationPrincipal Jwt jwt,PasswordUpdateDto dto){
        return appUserDetailsService.changePassword(jwt,dto);
    }



}
