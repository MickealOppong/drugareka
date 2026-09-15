package com.pages.controller;

import com.pages.dto.CartResponse;
import com.pages.dto.ResponseDto;
import com.pages.service.CartService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
@Slf4j
@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartService cartService;


    public CartController(CartService cartService) {
        this.cartService = cartService;
    }


    @PreAuthorize("hasAuthority('ROLE_USER')")
    @PostMapping("/new")
    public ResponseDto<Object> createCart(@AuthenticationPrincipal Jwt jwt, Long[] listingId) {
        return cartService.addItemToCart(listingId, jwt);
    }

    @GetMapping("/count")
    public ResponseEntity<Long> cartCount(@AuthenticationPrincipal Jwt jwt){
        return ResponseEntity.ok(cartService.getCartCount(jwt));
    }

    @GetMapping("/carts")
    public CartResponse cart(@AuthenticationPrincipal Jwt jwt){
       return cartService.getCart(jwt);
    }

    @DeleteMapping("/remove")
    public void deleteItem(@AuthenticationPrincipal Jwt jwt,Long listingId){
        cartService.removeItemFromCart(listingId,jwt);
    }
}
