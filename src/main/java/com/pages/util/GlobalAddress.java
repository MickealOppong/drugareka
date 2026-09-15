package com.pages.util;

import com.pages.dto.AddressResponse;
import com.pages.model.AppUser;
import jakarta.persistence.*;
import lombok.*;

@Builder
@Data
@Entity
@AllArgsConstructor
@NoArgsConstructor
public class GlobalAddress {

    @Id @GeneratedValue
    private Long id;
    private String city;
    private String country;
    private String street;
    private String postCode;
    private String contact;
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser appUser;


    public static AddressResponse toAddressResponse(GlobalAddress globalAddress){
        return AddressResponse.builder()
                .street(globalAddress.street)
                .city(globalAddress.city)
                .postalCode(globalAddress.postCode)
                .country(globalAddress.country)
                .contact(globalAddress.contact)
                .build();
    }
}
