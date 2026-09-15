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
    private Long userId;
    private Long listingId;

}
