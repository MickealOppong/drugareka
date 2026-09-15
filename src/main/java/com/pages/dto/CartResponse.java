package com.pages.dto;

import com.pages.enums.CartStatus;
import com.pages.model.AppUser;
import com.pages.model.Cart;
import com.pages.util.GlobalAddress;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CartResponse {

    private Long cartId;
    private CartStatus status;
    private AddressResponse address;
    private Long buyerId;
    List<CartItemDto> cartItemList;

}
