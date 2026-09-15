package com.pages.model;

import com.pages.util.LogEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
@Entity
@Table
@Builder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class RefreshToken extends LogEntity {

    @Id
    @GeneratedValue
    private Long id;
    private String refreshToken;
    private Instant expiredAt;
    private Instant issuedAt;

    @ManyToOne
    @JoinColumn(name = "fk_id",referencedColumnName = "Id")
    private AppUser appUser;
}