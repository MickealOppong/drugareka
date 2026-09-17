package com.pages.model;

import com.pages.dto.WishListResponse;
import com.pages.util.LogEntity;
import com.pages.util.Media;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class WishList  extends LogEntity {

    @Id @GeneratedValue
    private Long id;
    @ManyToOne
    @JoinColumn(name = "user_id",nullable = false)
    private AppUser user;
    @ManyToOne
    @JoinColumn(name = "listing_id",nullable = false)
    private ListingTransaction listingTransaction;

}
