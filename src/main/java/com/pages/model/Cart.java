package com.pages.model;


import com.pages.enums.CartStatus;
import com.pages.util.GlobalAddress;
import com.pages.util.LogEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "cart")
@Builder
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class Cart  extends LogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "buyer_id", nullable = false, unique = true)
    private AppUser buyer;

    @Enumerated(EnumType.STRING)
    private CartStatus status;


}
